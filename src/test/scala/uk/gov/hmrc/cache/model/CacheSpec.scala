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
