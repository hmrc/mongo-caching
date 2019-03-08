/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.libs.json._
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import reactivemongo.api.DB
import reactivemongo.api.commands.LastError
import reactivemongo.bson._
import reactivemongo.play.json.commands.{DefaultJSONCommandError, JSONFindAndModifyCommand}
import uk.gov.hmrc.cache.TimeToLive
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}


trait CacheRepository extends ReactiveRepository[Cache, Id] {
  def createOrUpdate[T](id: Id, key: String, toCache: T)(implicit writes: Writes[T]): Future[DatabaseUpdate[Cache]]
}

@deprecated("Please use injected CacheRepositoryFactory for CacheRepository creation", since = "6.x")
object CacheRepository extends MongoDbConnection {
  @deprecated("Please use injected CacheRepositoryFactory to CacheRepository creation", since = "6.x")
  def apply(collectionNameProvidedBySource: String,
            expireAfterSeconds: TimeToLive,
            cacheFormats: Format[Cache])
           (implicit ec: ExecutionContext): CacheRepository = {
    new CacheMongoRepository(collectionNameProvidedBySource, expireAfterSeconds, cacheFormats)
  }
}

class CacheRepositoryFactory @Inject()(reactiveMongoComponent: ReactiveMongoComponent,
                                       defaultExpireAfter: TimeToLive,
                                       executionContext: ExecutionContext) {

  private val defaultCacheFormats = Cache.mongoFormats

  def create(collection: String): CacheRepository =
    new CacheMongoRepository(collection, defaultExpireAfter, defaultCacheFormats)(reactiveMongoComponent.mongoConnector.db, executionContext)

  def create(collection: String, expireAfter: TimeToLive): CacheRepository =
    new CacheMongoRepository(collection, expireAfter, defaultCacheFormats)(reactiveMongoComponent.mongoConnector.db, executionContext)

  def create(collection: String, expireAfter: TimeToLive, cacheFormats: Format[Cache]): CacheRepository =
    new CacheMongoRepository(collection, expireAfter, cacheFormats)(reactiveMongoComponent.mongoConnector.db, executionContext)
}

class CacheMongoRepository(collName: String,
                           override val expireAfter: TimeToLive,
                           cacheFormats: Format[Cache] = Cache.mongoFormats)
                          (implicit mongo: () => DB, ec: ExecutionContext)
  extends ReactiveRepository[Cache, Id](collName, mongo, cacheFormats, Id.idFormats)
    with CacheRepository with TTLIndexing[Cache]
    with BSONBuilderHelpers {


  final val AtomicId = "atomicId"
  final val Id = "_id"

  import ReactiveMongoFormats._

  override def createOrUpdate[T](id: Id, key: String, value: T)(implicit writes: Writes[T]): Future[DatabaseUpdate[Cache]] = {
    val toCache = writes.writes(value)

    def upsert: Future[JSONFindAndModifyCommand.FindAndModifyResult] = withCurrentTime { time =>
      findAndUpdate(
        Json.obj(Id -> id.id),
        Json.obj(
          "$set" -> Json.obj(s"${Cache.DATA_ATTRIBUTE_NAME}.$key" -> toCache, "modifiedDetails.lastUpdated" -> time),
          "$setOnInsert" -> Json.obj("modifiedDetails.createdAt" -> time, Id -> id.id, AtomicId -> BSONObjectID.generate())
        ),
        upsert = true,
        fetchNewObject = true
      ).recoverWith {
        case e: DefaultJSONCommandError if e.code.contains(11000) => upsert // Retry after unique index violation
      }
    }

    def handleOutcome(outcome: JSONFindAndModifyCommand.FindAndModifyResult): Future[DatabaseUpdate[Cache]] = {
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

    upsert.flatMap(handleOutcome)
  }
}
