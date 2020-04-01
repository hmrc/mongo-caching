/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cache.repository

import org.joda.time.DateTime
import play.api.libs.json._
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands.{FindAndModifyCommand, LastError}
import reactivemongo.bson._
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}


trait CacheRepository extends ReactiveRepository[Cache, Id] with UniqueIndexViolationRecovery {

  def createOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]] = ???

  protected def saveOrUpdate(findQuery: => Future[Option[Cache]], ifNotFound: => Future[Cache], modifiers: (Cache) => Cache)(implicit ec: ExecutionContext): Future[DatabaseUpdate[Cache]] = ???
}

@deprecated("Please use injected CacheMongoRepository to your class", since = "6.x")
object CacheRepository extends MongoDbConnection {
  def apply(collectionNameProvidedBySource: String, expireAfterSeconds: Long, cacheFormats: Format[Cache])(implicit ec: ExecutionContext): CacheRepository =
    new CacheMongoRepository(collectionNameProvidedBySource, expireAfterSeconds, cacheFormats)
}

class CacheMongoRepository(collName: String, override val expireAfterSeconds: Long, cacheFormats: Format[Cache] = Cache.mongoFormats)(implicit mongo: () => DB, ec: ExecutionContext)
  extends ReactiveRepository[Cache, Id](collName, mongo, cacheFormats, Id.idFormats)
    with CacheRepository with TTLIndexing[Cache]
    with BSONBuilderHelpers {

  final val AtomicId = "atomicId"
  final val Id = "_id"

  import ReactiveMongoFormats._

  // It is possible with MongoDB to have a duplicate key violation when trying to upsert, if two or more threads try the operation concurrently: https://jira.mongodb.org/browse/SERVER-14322
  // Clients are expected to handle this appropriately. This library previously caught this error (E11000) and retried continually in the event it was detected.
  //
  // With a recent change to reactivemongo and reactivemongo-play-json, the error is wrapped up as a JsResultException instead.
  // The test that verified the exception was not thrown after simulated heavy load was then always giving false positives, as the
  // specific exception it was catching was never thrown.
  //
  // To avoid this happening again, we have added a test that specifically checks that the expected exception is thrown doing the upsert
  // without any recovery. To facilitate this, the upsert has been broken down into two pieces. This method, which will fail with the race condition,
  // and the method inside createOrUpdate below, which handles the recovery.
  //
  // See https://groups.google.com/forum/?fromgroups#!searchin/reactivemongo/JsResultException/reactivemongo/0vIVvi-T4jA/4Xc_G5tQAgAJ
  // and https://jira.tools.tax.service.gov.uk/browse/BDOG-731
  // for more context
  private[repository] def upsertMayFail(id: Id, key: String, toCache: JsValue)(implicit time: DateTime): Future[FindAndModifyCommand.Result[JSONSerializationPack.type]] = findAndUpdate(
    Json.obj(Id -> id.id),
    Json.obj(
      "$set" -> Json.obj(s"${Cache.DATA_ATTRIBUTE_NAME}.$key" -> toCache, "modifiedDetails.lastUpdated" -> time),
      "$setOnInsert" -> Json.obj("modifiedDetails.createdAt" -> time, Id -> id.id, AtomicId -> BSONObjectID.generate())
    ),
    upsert = true,
    fetchNewObject = true
  )

  override def createOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]] = {

    // Determine whether an E11000 exception was found
    def hasDupeKeyViolation(ex: JsResultException) = (for {
      validationErrors <- ex.errors.flatMap(_._2)
      message <- validationErrors.messages
      dupeKey = message.matches(".*code=11000[^\\w\\d].*")
    } yield dupeKey).contains(true)

    withCurrentTime { implicit time =>

      // In order to preserve the existing behavior, the ugly recovery fix has been kept as is, but updated to check the
      // JsResultException.
      // Code E11000 is the mongo code for a duplicate key violation
      def upsert(retries: Int = 3): Future[FindAndModifyCommand.Result[JSONSerializationPack.type]] = {
        val attempt = upsertMayFail(id, key, toCache)

        if (retries <= 1) attempt
        else attempt.recoverWith {
          case e: JsResultException if hasDupeKeyViolation(e) =>
            logger.debug(s"Detected an E11000 duplicate key violation. Retrying upsert. Attempts left: $retries")
            upsert(retries - 1)
        }
      }

      def handleOutcome(outcome: FindAndModifyCommand.Result[JSONSerializationPack.type]): Future[DatabaseUpdate[Cache]] = {
        (outcome.lastError, outcome.result[Cache]) match {
          case (Some(error), Some(value)) =>
            val lastError = LastError(
              ok = true,
              errmsg = None,
              code = None,
              lastOp = None,
              n = error.n,
              singleShard = None,
              updatedExisting = error.updatedExisting,
              upserted = None,
              wnote = None,
              wtimeout = false,
              waited = None,
              wtime = None,
              writeErrors = Nil,
              writeConcernError = None
            )
            if (error.updatedExisting) {
              Future.successful(DatabaseUpdate(lastError, Updated(value, value)))
            } else {
              Future.successful(DatabaseUpdate(lastError, Saved(value)))
            }
          case _ => throw new EntityNotFoundException("Failed to receive updated object!")
        }
      }

      upsert().flatMap(handleOutcome)
    }
  }
}
