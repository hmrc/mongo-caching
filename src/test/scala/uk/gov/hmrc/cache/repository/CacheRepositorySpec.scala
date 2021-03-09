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

package uk.gov.hmrc.cache.repository

import java.util.concurrent.Executors

import org.joda.time.DateTime
import org.scalacheck.Arbitrary._
import org.scalatest.{AppendedClues, BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.tagobjects.Retryable
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsResultException, Json}
import reactivemongo.api.ReadPreference
import reactivemongo.bson.{BSONLong, BSONObjectID}
import uk.gov.hmrc.cache.WordSpecLikeWithRetries
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo.{MongoSpecSupport, Saved, Updated}

import scala.collection.parallel.ExecutionContextTaskSupport
import scala.concurrent.ExecutionContext


class CacheRepositorySpec
  extends WordSpecLikeWithRetries
     with Matchers
     with MongoSpecSupport
     with BeforeAndAfterEach
     with OptionValues
     with EitherValues
     with ScalaFutures
     with Eventually
     with IntegrationPatience
     with ScalaCheckPropertyChecks
     with AppendedClues {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future

  private val expireAfter28DaysInSeconds = 60 * 60 * 24 * 7 * 4

  private def repo(name: String, expiresAfter: Long = expireAfter28DaysInSeconds) = new CacheMongoRepository(name, expiresAfter) {
    super.ensureIndexes.futureValue
  }

  override protected def beforeEach() = {
    mongoConnectorForTest.db().drop.futureValue
  }

  "create or update" should {
    "create" should {
      "insert a new record" in new TestSetup {
        val repository = repo("updateDataByKey")
        val id: Id = new Id("createMeId")

        val notFound = repository.findById(id).futureValue
        notFound shouldBe None

        val insertCheck = repository.createOrUpdate(id, "form1", sampleFormData1Json).futureValue
        insertCheck.updateType shouldBe a [Saved[_]]

        val original = repository.findById(id).futureValue.value

        original.id shouldBe id
        original.atomicId shouldBe defined
        (original.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json
      }
    }

    "delete" should {
      "be removed" in new TestSetup {
        val repository = repo("delete")

        val id: Id = "deleteMeId"

        val insertCheck = repository.createOrUpdate(id, "form1", sampleFormData1Json).futureValue
        insertCheck.updateType shouldBe a [Saved[_]]

        val original = repository.findById(id).futureValue.value
        original.id shouldBe id
        (original.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json

        repository.removeById(id).futureValue
        val removed = repository.findById(id).futureValue
        removed should be(empty)
      }
    }

    "update operations " should {

      "write and read JsValue type JsNumber (Integer) and verify same key can be removed with empty json object" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "numberId"
        val jsonNumber = Json.toJson(123)

        val insertCheck = repository.createOrUpdate(id, "form1", jsonNumber).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonNumber

        val unsetCheck = repository.createOrUpdate(id, "form1", Json.parse("{}")).futureValue
        unsetCheck.updateType shouldBe a[Updated[_]]
        val updateCheck = repository.findById(id).futureValue.value
        (updateCheck.data.value \ "form1").toEither.right.value shouldBe emptyJsonObject
      }

      // See the comment in Cache Repository for more context on this test and the one below
      // This test is tagged as Retryable and will be attempted a few times, as the race condition isn't triggered always,
      // but can be quite easily reproduced over a number of iterations
      "handle parallel processing - expect to trigger race condition when no recovery is present" taggedAs Retryable in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "id_" + arbitrary[Long].sample.value.toString //Use a random ID
        val jsonNumber = Json.toJson(arbitrary[Long].sample.value)

        val fixedPool = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50)) //Use a lot of threads
        val taskSupport = new ExecutionContextTaskSupport(fixedPool)

        val expectedToFail = Future.sequence {
          val parRange = (0 to 1000).par
          parRange.tasksupport = taskSupport
          parRange.map(_ => repository.upsertMayFail(id, "form1", jsonNumber)(DateTime.now)).toList
        }(implicitly, executor = fixedPool)

        whenReady(expectedToFail.failed) { f =>
          f shouldBe an [JsResultException] withClue "If no exception was thrown, the test was not aggressive enough to trigger the race condition on upsert"
          assert(f.getMessage.contains("E11000 duplicate key error collection"))
        }
      }

      // See the comment in Cache Repository for more context on this test and the one above
      "handle parallel processing - expect no race condition when using recovery" in new TestSetup {
        val fixedPool = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50)) //Use a lot of threads
        val taskSupport = new ExecutionContextTaskSupport(fixedPool)

        forAll(arbitrary[Long]) { l => //Try many times with arbitrary different values. Should never fail with the race condition
          val repository = repo("simpledata")
          val id: Id = "numberId"
          val jsonNumber = Json.toJson(l)

          Future.sequence {
            val parRange = (0 to 1000).par
            parRange.tasksupport = taskSupport
            parRange.map(_ => repository.createOrUpdate(id, "form1", jsonNumber)).toList
          }(implicitly, executor = fixedPool).futureValue

          val unsetCheck = repository.createOrUpdate(id, "form1", Json.parse("{}")).futureValue
          unsetCheck.updateType shouldBe a[Updated[_]]
          val updateCheck = repository.findById(id, ReadPreference.Primary).futureValue.value
          (updateCheck.data.value \ "form1").toEither.right.value shouldBe emptyJsonObject
        }
      }

      "write and read JsValue type JsNumber Double" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "numberDoubleId"
        val jsonNumber = Json.toJson(999.99)

        val insertCheck = repository.createOrUpdate(id, "form1", jsonNumber).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value

        (original.data.value \ "form1").toEither.right.value shouldBe jsonNumber
      }

      "write and read JsValue type JsString" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "stringId"
        val jsonString = Json.toJson("some simple string")

        val insertCheck = repository.createOrUpdate(id, "form1", jsonString).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonString
      }

      "write and read JsValue type JsBoolean" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "booleanId"
        val jsonBoolean = Json.toJson(true)

        val insertCheck = repository.createOrUpdate(id, "form1", jsonBoolean).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonBoolean
      }

      "write and read JsValue type JsArray Integer" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayIntId"
        val jsonArray = Json.toJson(List(1,2,3,4))

        val insertCheck = repository.createOrUpdate(id, "form1", jsonArray).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original: Cache = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonArray
      }

      "write and read JsValue type JsArray String" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayStringId"
        val jsonArray = Json.toJson(List("apple","pear","orange"))

        val insertCheck = repository.createOrUpdate(id, "form1", jsonArray).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonArray
      }

      "write and read JsValue type JsArray Double" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayDoubleId"
        val jsonArray = Json.toJson(List(123.11,456.22,789.33))

        val insertCheck = repository.createOrUpdate(id, "form1", jsonArray).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonArray
      }

      "write and read a JsValue type JsArray user-defined" in new TestSetup {
        val repository = repo("simpledata")
        val id: Id = "arrayUserDefinedId"

        case class SubUserDefined(a:String)
        case class UserDefined(a:String, b:String, c:Int, d:List[SubUserDefined])
        implicit val userDefinedFormats = Json.format[SubUserDefined]
        implicit val userDefinedFormats2 = Json.format[UserDefined]

        val jsonArray = Json.toJson(List(UserDefined("1","2",3, List(SubUserDefined("b"),SubUserDefined("cb"))),UserDefined("4","5",6,List(SubUserDefined("d")))))

        val insertCheck = repository.createOrUpdate(id, "form1", jsonArray).futureValue
        insertCheck.updateType shouldBe a[Saved[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "form1").toEither.right.value shouldBe jsonArray
      }

      "Saving a key with empty json will create a mongo record with no data attribute, " +
        "saving the same key with non empty json will update the record and create the data attribute with the specified key" in new TestSetup {

        val repository = repo("simpledata")
        val id: Id = "unsetId"

        repository.createOrUpdate(id, "searched-person", Json.parse("{}")).futureValue
        val shouldHaveNoData = repository.findById(id).futureValue.value
        (shouldHaveNoData.data.value  \ "searched-person").toEither.right.value  shouldBe Json.parse("{}")

        val insertCheck = repository.createOrUpdate(id, "searched-person", sampleFormData1Json).futureValue
        insertCheck.updateType shouldBe a[Updated[_]]
        val original = repository.findById(id).futureValue.value
        (original.data.value \ "searched-person").toEither.right.value shouldBe sampleFormData1Json
      }

      "once a record has been created, allow new records associated with the same key to be created and updated within a single operation" in new TestSetup {

        val repository = repo("updateDataByKey")
        val id: Id = "updateMeId"

        val insertCheck = repository.createOrUpdate(id, "form1", sampleFormData1Json).futureValue
        insertCheck.updateType shouldBe a [Saved[_]]
        val original = repository.findById(id).futureValue.value

        val updateCheck = repository.createOrUpdate(id, "form2", sampleFormData2Json).futureValue
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = repository.findById(id).futureValue.value
        updated.id shouldBe original.id
        updated.modifiedDetails.createdAt shouldBe original.modifiedDetails.createdAt
        updated.modifiedDetails.lastUpdated should not be original.modifiedDetails.lastUpdated

        (updated.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json
        (updated.data.value \ "form2").toEither.right.value shouldBe sampleFormData2Json

        repository.createOrUpdate(id, "form2", sampleFormData2JsonA).futureValue
        val updated2 = repository.findById(id).futureValue.value
        (updated2.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json
        (updated2.data.value \ "form2").toEither.right.value shouldBe sampleFormData2JsonA
      }
    }

    "update operations on legacy records (records which do not contain the new atomic Id) " should {

      "result in a successful update with the atomicId attribute NOT being created" in new TestSetup {

        val repository = repo("updateLegacy")
        val id: Id = "updateLegacyId"

        repository.insert(Cache(id, Some(Json.obj("form1" -> sampleFormData1Json)))).futureValue

        val original = repository.findById(id).futureValue.value
        original.atomicId shouldBe None

        val updateCheck = repository.createOrUpdate(id, "form2", sampleFormData2Json).futureValue
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = repository.findById(id).futureValue.value
        updated.atomicId shouldBe None

        updated.id shouldBe original.id
        updated.modifiedDetails.lastUpdated should not be original.modifiedDetails.lastUpdated

        (updated.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json
        (updated.data.value \ "form2").toEither.right.value shouldBe sampleFormData2Json
      }
    }

    "inserting a new key which contains an empty JsValue " should {
      "create a new record with a key with an empty Json object" in new TestSetup {
        val repository = repo("emptyJSValue")
        val id: Id = "testEmptyJsValueId"

        val updateCheck = repository.createOrUpdate(id, "form1", emptyJsonObject).futureValue
        updateCheck.updateType shouldBe a [Saved[_]]

        val original = repository.findById(id).futureValue.value
        original.atomicId.value shouldBe a [BSONObjectID]

        (original.data.value \ "form1").toEither.right.value shouldBe emptyJsonObject
      }
    }

    "applying empty optional fields to an existing fully populated form" should {
      "not set the optional fields" in new TestSetup {
        val repository = repo("unsetJSValue")
        val id: Id = "testClearJsValueIdA"

        val insertCheck = repository.createOrUpdate(id, "form1", sampleFormData1Json).futureValue
        insertCheck.updateType shouldBe a [Saved[_]]

        val original = repository.findById(id).futureValue.value
        original.atomicId.value shouldBe a [BSONObjectID]
        (original.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json

        // Verify optional fields can be removed.
        val attributeRemoval = repository.createOrUpdate(id, "form1", sampleFormData1Json2).futureValue
        attributeRemoval.updateType shouldBe a [Updated[_]]

        val checkAttributeRemoval = repository.findById(id).futureValue.value
        checkAttributeRemoval.atomicId.value shouldBe a [BSONObjectID]
        (checkAttributeRemoval.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json2

        // Verify complete object can be emptied.
        val updateRemoval = repository.createOrUpdate(id, "form1", emptyJsonObject).futureValue

        updateRemoval.updateType shouldBe a [Updated[_]]
        val updated = repository.findById(id).futureValue.value
        updated.atomicId.value shouldBe a [BSONObjectID]

        (updated.data.value \ "form1").toEither.right.value shouldBe emptyJsonObject
      }
    }

    "applying empty JSON on an existing key " should {

      "only unset the JSON contents of the key that was supplied with empty json object and leave the other key untouched" in new TestSetup {

        val repository = repo("unsetJSValue")
        val id: Id = "testClearJsValueIdB"

        val insertCheck = repository.createOrUpdate(id, "form1", sampleFormData1Json).futureValue
        insertCheck.updateType shouldBe a [Saved[_]]

        val insertCheck2 = repository.createOrUpdate(id, "form2", sampleFormData2Json).futureValue
        insertCheck2.updateType shouldBe a [Updated[_]]

        val original = repository.findById(id).futureValue.value
        original.atomicId.value shouldBe a [BSONObjectID]
        (original.data.value \ "form1").toEither.right.value shouldBe sampleFormData1Json
        (original.data.value \ "form2").toEither.right.value shouldBe sampleFormData2Json

        val updateCheck = repository.createOrUpdate(id, "form1", emptyJsonObject).futureValue
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = repository.findById(id).futureValue.value
        updated.atomicId.value shouldBe a [BSONObjectID]

        (updated.data.value \ "form1").toEither.right.value.asOpt[String] shouldBe None
        (updated.data.value \ "form2").toEither.right.value shouldBe sampleFormData2Json
      }
    }
  }

  "KeyStoreMongoRepository" should {
    "replace the value expireAfterSeconds in the index dateUpdatedIndex when the configuration value expireAfterSeconds has changed" in {
      val repository = repo("replaceIndex")
      val indexes = repository.collection.indexesManager.list().futureValue
      indexes.size shouldNot be(0)
      val oldIndex = indexes.find { index =>
        index.eventualName == "lastUpdatedIndex"
      }
      oldIndex.isDefined shouldBe true
      oldIndex.value.options.get("expireAfterSeconds") shouldBe Some(BSONLong(expireAfter28DaysInSeconds))
      val modifiedRepository = repo("replaceIndex", 8888888)
      eventually {
        val index = modifiedRepository.collection.indexesManager.list().futureValue
                      .find(index => index.eventualName == "lastUpdatedIndex")
        index.value.options.get("expireAfterSeconds").value shouldBe BSONLong(8888888)
      }
    }

    "do not find document" in {
      val repository = repo("doNotFindRepo")
      val id: Id = "someId"
      repository.findById(id).futureValue.isEmpty shouldBe true
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

    lazy val sampleFormData2JsonDeltaOnlyUsername =
      Json.parse( """{
        |"form-field-username":"Different Username"
        }""".stripMargin)
  }
}
