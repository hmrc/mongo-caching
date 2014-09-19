package uk.gov.hmrc.cache.model

import org.joda.time.DateTime
import org.scalatest.LoneElement
import play.api.libs.json._
import uk.gov.hmrc.mongo.CreationAndLastModifiedDetail
import org.scalatest.{WordSpecLike, Matchers}

class CachableSpec extends WordSpecLike with Matchers with LoneElement {

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

  "data field" should {

    "be updated with the provided value" in {
      val ks = new Cacheable {

        override def updateData(key: String, value: JsValue): Cacheable = ???

        override def markUpdated(implicit updatedTime: DateTime): Cacheable = ???

        override val modifiedDetails: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail()
        override val data: Option[JsValue] = Some(json)
        override val id: Id = "myId"
      }

      val updatedJson = Json.parse("""{"form-field-username" : "Duncan Boss"}""".stripMargin)
      val jsObject = ks.findAndReplaceTransformer(json, "form1", updatedJson)

      jsObject shouldBe defined

      val form1 = jsObject.get \\ "form1"

      form1.loneElement shouldBe updatedJson

      val form2 = jsObject.get \\ "form2"

      form2.loneElement shouldBe json \ "form2"
    }

  }

}
