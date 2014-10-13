/*
 * Copyright 2014 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package uk.gov.hmrc.cache.repository

import play.api.libs.json.{Format, JsObject, JsValue}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo._

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
  with CacheRepository with TTLIndexing[Cache] {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def createOrUpdate(id: Id, key: String, toCache: JsValue) = {
    withCurrentTime {
      implicit time =>
        saveOrUpdate(
          findQuery = findById(id),
          ifNotFound = Future.successful(Cache(id, Some(JsObject(Seq(key -> toCache))))),
          modifiers = _.updateData(key, toCache).markUpdated
        )
    }
  }

  override protected def saveOrUpdate(findQuery: => Future[Option[Cache]], ifNotFound: => Future[Cache], modifiers: (Cache) => Cache = a => a)(implicit ec: ExecutionContext): Future[DatabaseUpdate[Cache]] = {
    withCurrentTime {
      implicit time =>
        val updateTypeF = findQuery.flatMap {
          case Some(existingValue) => Future.successful(Updated(existingValue, modifiers(existingValue)))
          case None => ifNotFound.map(newValue => Saved(modifiers(newValue))): Future[UpdateType[Cache]]
        }

        updateTypeF.flatMap {
          updateType =>
            save(updateType.savedValue).map {
              lastErr =>
                DatabaseUpdate(writeResult = lastErr, updateType)
            }
        }
    }
  }
}