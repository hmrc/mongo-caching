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

import org.scalatest.LoneElement
import play.api.libs.json._
import org.scalatest.{WordSpecLike, Matchers}

class CacheSpec extends WordSpecLike with Matchers with LoneElement {

  val json = Json.parse( """ {
        |"form1": {
            |"form-field-username": "John Densemore",
            |"form-field-password": "password1",
            |"form-field-address-number": 42,
            |"form-field-address-one": "The Door"
            |},
        |"form2": {
            |"form-field-username": "Mark Dearnely",
            |"form-field-password": "ihaveaplan",
            |"form-field-address-number": 1,
            |"form-field-address-one": "HMRC Road"
           |}
        |}""".stripMargin)

  "data keys" should {
    "return all set keys" in {
      val ks = Cache("hasSomeKeysId", Some(json))

      ks.dataKeys() should not be None

      ks.dataKeys().get shouldBe Set("form1", "form2")
    }

    "return None" in {
      val ks = Cache("hasSomeKeysId", None)
      ks.dataKeys() shouldBe None
    }
  }

  "Cache" should {
    "have json with 'id'" in {
      val cache = Cache("theId", Some(json))

      (Json.toJson(cache) \ "id").as[String] shouldBe "theId"
    }

    "have mongo json with '_id'" in {
      implicit val mongoFormats = Cache.mongoFormats

      val cache = Cache("theId", Some(json))

      (Json.toJson(cache) \ "_id").as[String] shouldBe "theId"
    }
  }
}
