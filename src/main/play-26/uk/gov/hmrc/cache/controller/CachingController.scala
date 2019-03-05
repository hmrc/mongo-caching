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

package uk.gov.hmrc.cache.controller

import play.api.libs.json.{Json, Writes}
import play.api.mvc.{BaseController, Request, Result}
import reactivemongo.api.commands.WriteConcern
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.cache.repository.CacheRepositoryFactory

import scala.concurrent.{ExecutionContext, Future}

trait CachingController { self: BaseController =>

  import play.api.libs.json.JsValue
  import play.api.libs.json.Json._

  def cacheRepositoryFactory: CacheRepositoryFactory

  private def keyStoreRepository(source: String) = cacheRepositoryFactory.create(source)

  def find[A](source: String, id: String)(implicit w: Writes[A]) = keyStoreRepository(source).findById(id)(controllerComponents.executionContext).map {
    case Some(cacheable) => Ok(toJson(safeConversion(cacheable)))
    case _ => NotFound("No entity found")
  }(controllerComponents.executionContext)

  def dataKeys(source: String, id: String) = keyStoreRepository(source).findById(id)(controllerComponents.executionContext).map {
    case Some(ks) => Ok(toJson(ks.dataKeys()))
    case _ => NotFound("No entity found")
  }(controllerComponents.executionContext)

  private def safeConversion(cacheable:Cache) = {
    cacheable.data match {
      case None => cacheable.copy(data = Some(Json.parse("{}")))
      case _ => cacheable
    }
  }

  def add(source: String, id: String, key: String)(extractBody: ((JsValue) => Future[Result]) => Future[Result])(implicit request: Request[JsValue]): Future[Result] = {
    if (key contains '.') {
      Future.successful(BadRequest("A cacheable key cannot contain dots"))
    } else {
      extractBody { jsBody =>
        keyStoreRepository(source).createOrUpdate(id, key, jsBody).map(result => {
          Ok(toJson(safeConversion(result.updateType.savedValue)))
        })(controllerComponents.executionContext)
      }
    }
  }

  def remove(source: String, id: String) = keyStoreRepository(source).removeById(id, WriteConcern.Default)(controllerComponents.executionContext).map {
    case lastError if lastError.ok => NoContent
  }(controllerComponents.executionContext).recover {
    case t  => InternalServerError(s"Failed to remove entity '$id' from source '$source'. Error: ${t.getMessage}")
  }(controllerComponents.executionContext)
}
