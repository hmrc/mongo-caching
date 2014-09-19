package uk.gov.hmrc.cache.model

import org.scalatest.{WordSpecLike, Matchers}

class IdSpec extends WordSpecLike with Matchers {

  import play.api.libs.json._
  import Id.idFormats


  "Id formats" should {
    "write" in {
      val id : Id = "idValue"
      val json = Json.toJson(id)
      Json.stringify(json) shouldBe """"idValue""""
    }
  }
}
