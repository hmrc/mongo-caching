/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cache.model

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.CreationAndLastModifiedDetail

case class Cache(
  id             : Id,
  data           : Option[JsValue]               = None,
  modifiedDetails: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
  atomicId       : Option[BSONObjectID]          = None
) extends Cacheable

object Cache {
  import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

  final val DATA_ATTRIBUTE_NAME = "data"

  implicit val format = ReactiveMongoFormats.objectIdFormats
  implicit val cacheFormat = Json.format[Cache]

  val mongoFormats = ReactiveMongoFormats.mongoEntity {

    implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
    cacheFormat
  }
}
