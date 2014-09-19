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
 package uk.gov.hmrc.cache.model

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.mongo.CreationAndLastModifiedDetail

import scala.collection.Set

trait Cacheable {

  import org.joda.time.DateTime

  val id: Id
  val data: Option[JsValue]
  val modifiedDetails: CreationAndLastModifiedDetail

  def dataKeys(): Option[Set[String]] = data.map(js => js.as[JsObject].keys)

  def markUpdated(implicit updatedTime: DateTime) : Cacheable

  def updateData(key: String, value: JsValue) : Cacheable


  protected def transformData(key: String, value: JsValue) = data match {
    case Some(js: JsValue) => findAndReplaceTransformer(js, key, value)
    case _ =>
      Logger.info(s"The cached entity with id '${id.id}' contained no data. This should not be possible.")
      throw new InvalidStateException(s"The cached entity with id '${id.id}' contained no data")
  }

  private [model] def findAndReplaceTransformer(toTransform: JsValue, key: String, value: JsValue) = {
    toTransform.transform {
      (__ \ key).json.prune andThen __.json.update(__.read[JsObject].map {
        o => o ++ JsObject(Seq(key -> value))
      })
    } fold(
      errors => throw new Exception("Failed to update the defined path" + errors), //FIXME: is throwing an exception correct?
      success => Some(success)
      )
  }

  class InvalidStateException(msg: String) extends Exception(msg)
}
