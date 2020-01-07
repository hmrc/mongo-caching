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

package uk.gov.hmrc.cache.model

import play.api.libs.json.{JsError, JsResult, JsString, JsValue}

case class Id(id: String)

object Id {

  import play.api.libs.json.{Format, Reads, Writes}

  implicit def stringToId(s: String) = new Id(s)

  private val idWrite: Writes[Id] = new Writes[Id] {
    override def writes(value: Id): JsValue = JsString(value.id)
  }

  private val idRead: Reads[Id] = new Reads[Id] {
    override def reads(js: JsValue): JsResult[Id] = js match {
      case v: JsString => v.validate[String].map(Id.apply)
      case noParsed => JsError(s"Could not read Json value of 'id' in $noParsed")
    }
  }
  implicit val idFormats = Format(idRead, idWrite)
}
