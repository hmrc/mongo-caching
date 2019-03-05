package uk.gov.hmrc.cache.controller

import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Controller, Request, Result}
import reactivemongo.api.commands.WriteConcern
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.cache.repository.CacheRepositoryFactory

import scala.concurrent.{ExecutionContext, Future}

trait CachingController { self: Controller =>

  import play.api.libs.json.JsValue
  import play.api.libs.json.Json._

  def cacheRepositoryFactory: CacheRepositoryFactory

  def executionContext: ExecutionContext

  private def keyStoreRepository(source: String) = cacheRepositoryFactory.create(source)

  def find[A](source: String, id: String)(implicit w: Writes[A]) = keyStoreRepository(source).findById(id)(executionContext).map {
    case Some(cacheable) => Ok(toJson(safeConversion(cacheable)))
    case _ => NotFound("No entity found")
  }(executionContext)

  def dataKeys(source: String, id: String) = keyStoreRepository(source).findById(id)(executionContext).map {
    case Some(ks) => Ok(toJson(ks.dataKeys()))
    case _ => NotFound("No entity found")
  }(executionContext)

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
        })(executionContext)
      }
    }
  }

  def remove(source: String, id: String) = keyStoreRepository(source).removeById(id, WriteConcern.Default)(executionContext).map {
    case lastError if lastError.ok => NoContent
  }(executionContext).recover {
    case t  => InternalServerError(s"Failed to remove entity '$id' from source '$source'. Error: ${t.getMessage}")
  }(executionContext)
}
