/*
 * Copyright 2015 HM Revenue & Customs
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

import play.api.libs.json.{JsArray, Format, JsObject, JsValue}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.json.BSONFormats
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

 import scala.concurrent.{ExecutionContext, Future}


trait CacheRepository extends Repository[Cache, Id] {

  def createOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]] = ???

  protected def saveOrUpdate(findQuery: => Future[Option[Cache]], ifNotFound: => Future[Cache], modifiers: (Cache) => Cache)(implicit ec: ExecutionContext): Future[DatabaseUpdate[Cache]] = ???
}

object CacheRepository extends MongoDbConnection {

  def apply(collectionNameProvidedBySource: String, expireAfterSeconds: Long, cacheFormats: Format[Cache]): CacheRepository = new CacheMongoRepository(collectionNameProvidedBySource, expireAfterSeconds, cacheFormats)
}

class CacheMongoRepository(collName: String, override val expireAfterSeconds: Long, cacheFormats: Format[Cache] = Cache.mongoFormats)(implicit mongo: () => DB)
  extends ReactiveRepository[Cache, Id](collName, mongo, cacheFormats, Id.idFormats)
  with CacheRepository with TTLIndexing[Cache]
  with AtomicUpdate[Cache]
  with BSONBuilderHelpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  def findByIdBSON(id: Id) = BSONDocument(DEFAULT_ID -> BSONString(id.id))

  private def modifierForCrudCredentialsBSON(time: Long): BSONDocument = BSONDocument(
    "$set" -> BSONDocument("modifiedDetails.lastUpdated" -> BSONDateTime(time)),
    "$setOnInsert" -> BSONDocument("modifiedDetails.createdAt" -> BSONDateTime(time))
  )

  def allKeys(json: JsValue): Seq[String] = json match {
    case JsObject(fields) => fields.map(_._1) ++ fields.map(_._2).flatMap(allKeys)
    case JsArray(as) => as.flatMap(allKeys)
    case _ => Seq.empty[String]
  }

  // Build the key that will be used to write to mongo.
  def buildKey(a:String, b:String) = s"${Cache.DATA_ATTRIBUTE_NAME}.$a.$b"

  override def createOrUpdate(id: Id, key: String, toCache: JsValue) = {

    withCurrentTime {

      implicit time =>

        // Build the BSON update command based on the contents of the JSValue.
        val toCacheUpdateDocument = for {
          k <- allKeys(toCache)
        } yield (set(BSONDocument(buildKey(key,k) -> BSONFormats.toBSON(toCache \ k).get)))

        // Generate a single BSON update operation to be applied to mongo for the collection.
        val modifier = List(
          modifierForCrudCredentialsBSON(time.getMillis),
          setOnInsert(BSONDocument("_id" -> BSONString(id.id)))
        )
        val modifiers=(toCacheUpdateDocument.toList ::: modifier).reduceLeft(_ ++ _)
        atomicSaveOrUpdate(findByIdBSON(id.id), modifiers, upsert = true, "atomicId").map(_.getOrElse(throw atomicError))
    }
  }

  private def atomicError = new EntityNotFoundException(s"Failed to receive updated object from atomics!")

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: Cache) = {

    oldRecord.data match {
      case Some(id) => newRecordId.equals(id)
      case _        => false
    }
  }

}
