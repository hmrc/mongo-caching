/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.cache.repository

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpecLike}
import play.api.libs.json.Json
import reactivemongo.bson.{BSONLong, BSONObjectID}
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo.{MongoSpecSupport, Saved, Updated}


class CacheRepositorySpec extends WordSpecLike with Matchers with MongoSpecSupport with BeforeAndAfterEach with Eventually {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import scala.concurrent.{Await, Future}

  implicit val defaultTimeout = 5 seconds

  def await[A](future: Future[A])(implicit timeout: Duration) = Await.result(future, timeout)


  private val expireAfter28DaysInSeconds = 60 * 60 * 24 * 7 * 4

  private def repo(name: String, expiresAfter: Long = expireAfter28DaysInSeconds) = new CacheMongoRepository(name, expiresAfter) {
    await(super.ensureIndexes)
  }

  override protected def beforeEach() = {
    mongoConnectorForTest.db().drop
  }

  "create or update" should {

    "create" should {

      "insert a new record" in new TestSetup {
        val repository = repo("updateDataByKey")
        val id: Id = new Id("createMeId")

        val notFound = await(repository.findById(id))
        notFound shouldBe None

        val insertCheck = await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        insertCheck.updateType shouldBe a [Saved[_]]

        val original = await(repository.findById(id)).get

        original.id shouldBe id
        Option(original.modifiedDetails.createdAt) shouldBe defined
        Option(original.modifiedDetails.lastUpdated) shouldBe defined
        Option(original.atomicId.get) shouldBe defined
        original.data.get \ "form1" shouldBe sampleFormData1Json
      }
    }

    "delete" should {
      "be removed" in new TestSetup {
        val repository = repo("delete")

        val id: Id = "deleteMeId"

        val insertCheck = await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        insertCheck.updateType shouldBe a [Saved[_]]

        val original = await(repository.findById(id)).get
        original.id shouldBe id
        original.data.get \ "form1" shouldBe sampleFormData1Json

        await(repository.removeById(id))
        val removed = await(repository.findById(id))
        removed should be(empty)
      }
    }

    "update operations " should {

      "write and read JsValue type JsNumber (Integer) and verify same key can be removed with empty json object" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "numberId"
        val jsonNumber = Json.toJson(123)

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonNumber))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonNumber

        val unsetCheck = await(repository.createOrUpdate(id, "form1", Json.parse("{}")))
        unsetCheck.updateType shouldBe a[Updated[_]]
        val updateCheck = await(repository.findById(id)).get
        updateCheck.data.get \ "form1" shouldBe emptyJsonObject
      }

      "write and read JsValue type JsNumber Double" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "numberDoubleId"
        val jsonNumber = Json.toJson(999.99)

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonNumber))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get

        original.data.get \ "form1" shouldBe jsonNumber
      }

      "write and read JsValue type JsString" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "stringId"
        val jsonString = Json.toJson("some simple string")

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonString))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonString
      }

      "write and read JsValue type JsBoolean" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "booleanId"
        val jsonBoolean = Json.toJson(true)

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonBoolean))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonBoolean
      }

      "write and read JsValue type JsArray Integer" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayIntId"
        val jsonArray = Json.toJson(List(1,2,3,4))

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonArray))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original: Cache = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonArray
      }

      "write and read JsValue type JsArray String" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayStringId"
        val jsonArray = Json.toJson(List("apple","pear","orange"))

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonArray))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonArray
      }

      "write and read JsValue type JsArray Double" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayDoubleId"
        val jsonArray = Json.toJson(List(123.11,456.22,789.33))

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonArray))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonArray
      }

      "write and read a JsValue type JsArray user-defined" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayUserDefinedId"

        case class SubUserDefined(a:String)
        case class UserDefined(a:String, b:String, c:Int, d:List[SubUserDefined])
        implicit val userDefinedFormats = Json.format[SubUserDefined]
        implicit val userDefinedFormats2 = Json.format[UserDefined]

        val jsonArray = Json.toJson(List(UserDefined("1","2",3, List(SubUserDefined("b"),SubUserDefined("cb"))),UserDefined("4","5",6,List(SubUserDefined("d")))))

        val insertCheck = await(repository.createOrUpdate(id, "form1", jsonArray))
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "form1" shouldBe jsonArray
      }

      "Saving a key with empty json will create a mongo record with no data attribute, " +
        "saving the same key with non empty json will update the record and create the data attribute with the specified key" in new TestSetup {

        val repository = repo("simpledata")
        val id: Id = "unsetId"

        await(repository.createOrUpdate(id, "searched-person", Json.parse("{}")))
        val shouldHaveNoData = await(repository.findById(id)).get
        shouldHaveNoData.data.get  \ "searched-person"  shouldBe Json.parse("{}")

        val insertCheck = await(repository.createOrUpdate(id, "searched-person", sampleFormData1Json))
        insertCheck.updateType shouldBe a[Updated[_]]
        val original = await(repository.findById(id)).get
        original.data.get \ "searched-person" shouldBe sampleFormData1Json
      }

      "once a record has been created, allow new records associated with the same key to be created and updated within a single operation" in new TestSetup {

        val repository = repo("updateDataByKey")
        val id: Id = "updateMeId"

        val insertCheck = await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        insertCheck.updateType shouldBe a [Saved[_]]
        val original = await(repository.findById(id)).get

        val updateCheck = await(repository.createOrUpdate(id, "form2", sampleFormData2Json))
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = await(repository.findById(id)).get
        updated.id shouldBe original.id
        updated.modifiedDetails.createdAt shouldBe original.modifiedDetails.createdAt
        updated.modifiedDetails.lastUpdated should not be original.modifiedDetails.lastUpdated

        updated.data.get \ "form1" shouldBe sampleFormData1Json
        updated.data.get \ "form2" shouldBe sampleFormData2Json

        await(repository.createOrUpdate(id, "form2", sampleFormData2JsonA))
        val updated2 = await(repository.findById(id)).get
        updated2.data.get \ "form1" shouldBe sampleFormData1Json
        updated2.data.get \ "form2" shouldBe sampleFormData2JsonA
      }
    }

    "update operations on legacy records (records which do not contain the new atomic Id) " should {

      "result in a successful update with the atomicId attribute NOT being created" in new TestSetup {

        val repository = repo("updateLegacy")
        val id: Id = "updateLegacyId"

        val res = await(repository.insert(Cache(id, Some(Json.obj("form1" -> sampleFormData1Json)))))

        val original = await(repository.findById(id)).get
        original.atomicId shouldBe None

        val updateCheck = await(repository.createOrUpdate(id, "form2", sampleFormData2Json))
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = await(repository.findById(id)).get
        updated.atomicId shouldBe None

        updated.id shouldBe original.id
        updated.modifiedDetails.lastUpdated should not be original.modifiedDetails.lastUpdated

        updated.data.get \ "form1" shouldBe sampleFormData1Json
        updated.data.get \ "form2" shouldBe sampleFormData2Json
      }

    }

    "inserting a new key which contains an empty JsValue " should {

      "create a new record with a key with an empty Json object" in new TestSetup {

        val repository = repo("emptyJSValue")
        val id: Id = "testEmptyJsValueId"

        val updateCheck = await(repository.createOrUpdate(id, "form1", emptyJsonObject))
        updateCheck.updateType shouldBe a [Saved[_]]

        val original = await(repository.findById(id)).get
        original.atomicId.get shouldBe a [BSONObjectID]

        original.data.get \ "form1" shouldBe emptyJsonObject
      }
    }

    "applying empty optional fields to an existing fully populated form" should {

      "not set the optional fields" in new TestSetup {

        val repository = repo("unsetJSValue")
        val id: Id = "testClearJsValueIdA"

        val insertCheck = await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        insertCheck.updateType shouldBe a [Saved[_]]

        val original = await(repository.findById(id)).get
        original.atomicId.get shouldBe a [BSONObjectID]
        original.data.get \ "form1" shouldBe sampleFormData1Json

        // Verify optional fields can be removed.
        val attributeRemoval = await(repository.createOrUpdate(id, "form1", sampleFormData1Json2))
        attributeRemoval.updateType shouldBe a [Updated[_]]

        val checkAttributeRemoval = await(repository.findById(id)).get
        checkAttributeRemoval.atomicId.get shouldBe a [BSONObjectID]
        checkAttributeRemoval.data.get \ "form1" shouldBe sampleFormData1Json2

        // Verify complete object can be emptied.
        val updateRemoval = await(repository.createOrUpdate(id, "form1", emptyJsonObject))

        updateRemoval.updateType shouldBe a [Updated[_]]
        val updated = await(repository.findById(id)).get
        updated.atomicId.get shouldBe a [BSONObjectID]

        updated.data.get \ "form1" shouldBe emptyJsonObject
      }

    }

    "applying empty JSON on an existing key " should {

      "only unset the JSON contents of the key that was supplied with empty json object and leave the other key untouched" in new TestSetup {

        val repository = repo("unsetJSValue")
        val id: Id = "testClearJsValueIdB"

        val insertCheck = await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        insertCheck.updateType shouldBe a [Saved[_]]

        val insertCheck2 = await(repository.createOrUpdate(id, "form2", sampleFormData2Json))
        insertCheck2.updateType shouldBe a [Updated[_]]

        val original = await(repository.findById(id)).get
        original.atomicId.get shouldBe a [BSONObjectID]
        original.data.get \ "form1" shouldBe sampleFormData1Json
        original.data.get \ "form2" shouldBe sampleFormData2Json

        val updateCheck = await(repository.createOrUpdate(id, "form1", emptyJsonObject))
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = await(repository.findById(id)).get
        updated.atomicId.get shouldBe a [BSONObjectID]

        (updated.data.get \ "form1").asOpt[String] shouldBe None
        updated.data.get \ "form2" shouldBe sampleFormData2Json
      }
    }

  }

  "KeyStoreMongoRepository" should {
    "replace the value expireAfterSeconds in the index dateUpdatedIndex when the configuration value expireAfterSeconds has changed" in {
      val repository = repo("replaceIndex")
      val indexes = await(repository.collection.indexesManager.list())
      indexes.size shouldNot be(0)
      val oldIndex = indexes.find(index => {
        index.eventualName == "lastUpdatedIndex"
      })
      oldIndex.isDefined shouldBe true
      oldIndex.get.options.get("expireAfterSeconds").get shouldBe BSONLong(expireAfter28DaysInSeconds)
      val modifiedRepository = repo("replaceIndex", 8888888)
      eventually {
        val index = await(modifiedRepository.collection.indexesManager.list()).find(index => index.eventualName == "lastUpdatedIndex")
        index.isDefined shouldBe true
        index.get.options.get("expireAfterSeconds").get shouldBe BSONLong(8888888)
      }
    }

    "do not find document" in {
      val repository = repo("doNotFindRepo")
      val id: Id = "someId"
      await(repository.findById(id)).isEmpty shouldBe true
    }

    // This test is intentionally commented out. Enable it locally to test that the removal of expired documents works.
//    "delete a document after it is not updated for longer than the \'expireAfter\' amount of time" ignore new TestSetup {
//      val repository = repo("expireDocument", 2)
//      val id = Id("expireTestId")
//      await(repository.createOrUpdate(id, "form", sampleFormData1Json))
//      Thread.sleep(60000)
//      val doc = await(repository.findById(id))
//      doc shouldBe None
//    }
  }

  private trait TestSetup {

    val emptyJsonObject = Json.parse("{}")

    lazy val sampleFormData1Json = Json.parse( """{
                                                 |"form-field-username":"John Densemore",
                                                 |"form-field-password":"password1",
                                                 |"form-field-address-number":42,
                                                 |"form-field-address-one":"The Door",
                                                 |"inner1" : {
                                                 |  "a" : "inner data depth 1",
                                                 |  "inner2" : {
                                                 |    "b" : "inner data depth 2"
                                                 |  }
                                                 | }
                                                 |}""".stripMargin)

    lazy val sampleFormData1Json2 = Json.parse( """{
                                                    |"form-field-username":"John Densemore",
                                                    |"form-field-password":"password1",
                                                    |"inner1" : {
                                                    |  "a" : "inner data depth 1",
                                                    |  "inner2" : {
                                                    |    "b" : "inner data depth 2"
                                                    |  }
                                                    | }
                                                    |}""".stripMargin)


    lazy val sampleFormData2Json = Json.parse( """{
                                                 |"form-field-username":"Mark Dearnely",
                                                 |"form-field-password":"ihaveaplan",
                                                 |"form-field-address-number": 1,
                                                 |"form-field-address-one":"HMRC Road"
                                                 |}""".stripMargin)

    lazy val sampleFormData2JsonA = Json.parse( """{
                                                  |"form-field-username":"Different Username",
                                                  |"form-field-password":"ihaveaplan",
                                                  |"form-field-address-number": 1,
                                                  |"form-field-address-one":"HMRC Road"
                                                  |}""".stripMargin)

    lazy val sampleFormData2JsonDeltaOnlyUsername = Json.parse( """{
    |"form-field-username":"Different Username"
    }""".stripMargin)

  }

}
