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
 package uk.gov.hmrc.cache.controller

import play.api.libs.json.{Format, Writes}
import play.api.mvc.{Controller, Result, Request}
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.cache.TimeToLive
import uk.gov.hmrc.cache.model.Cache

import scala.concurrent.Future

trait CachingController extends MongoDbConnection with TimeToLive {
  self: Controller =>

  import play.api.libs.json.JsValue
  import play.api.libs.json.Json._
  import uk.gov.hmrc.cache.repository.CacheRepository
  import uk.gov.hmrc.play.http.BadRequestException

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val cacheMongoFormats: Format[Cache] = Cache.mongoFormats

  private def keyStoreRepository(source: String) = CacheRepository(source, defaultExpireAfter, cacheMongoFormats)

  def find[A](source: String, id: String)(implicit w: Writes[A]) = keyStoreRepository(source).findById(id).map {
    case Some(cacheable) => Ok(toJson(cacheable))
    case _ => NotFound("No entity found")
  }

  def dataKeys(source: String, id: String) = keyStoreRepository(source).findById(id).map {
    case Some(ks) => Ok(toJson(ks.dataKeys()))
    case _ => NotFound("No entity found")
  }

  def add(source: String, id: String, key: String)(extractBody: ((JsValue) => Future[Result]) => Future[Result])(implicit request: Request[JsValue]) = {
    if (key contains '.') throw new BadRequestException("A cacheable key cannot contain dots")
    extractBody { jsBody =>
      keyStoreRepository(source).createOrUpdate(id, key, jsBody).map(result => Ok(toJson(result.updateType.savedValue)))
    }
  }

  def remove(source: String, id: String) = keyStoreRepository(source).removeById(id).map {
    case lastError if lastError.ok => NoContent
  }.recover {
    case t  => InternalServerError(s"Failed to remove entity '$id' from source '$source'. Error: ${t.getMessage}")
  }
}
