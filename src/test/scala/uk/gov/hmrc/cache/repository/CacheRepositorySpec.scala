/*
 * Copyright 2015 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json}
import reactivemongo.bson.{BSONObjectID, BSONLong}
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo.{Saved, MongoSpecSupport, Updated}


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

        repository.removeById(id)
        val removed = await(repository.findById(id))
        removed should be(empty)
      }
    }

    "update operations " should {
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

        await(repository.save(Cache(id, Some(Json.obj("form1" -> sampleFormData1Json)))))
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

    "inserting a new record with a key which contains an empty JSValue " should {

      "create a new record with no key" in new TestSetup {

        val repository = repo("emptyJSValue")
        val id: Id = "testEmptyJsValueId"

        val updateCheck = await(repository.createOrUpdate(id, "form1", Json.parse("{}")))
        updateCheck.updateType shouldBe a [Saved[_]]

        val original = await(repository.findById(id)).get
        original.atomicId.get shouldBe a [BSONObjectID]
        original.data shouldBe None
      }
    }

    "applying empty JSON on an existing key " should {

      "only clear the JSON contents of the key that was supplied with empty JSon" in new TestSetup {

        val repository = repo("unsetJSValue")
        val id: Id = "testClearJsValueId"

        val insertCheck = await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        insertCheck.updateType shouldBe a [Saved[_]]

        val insertCheck2 = await(repository.createOrUpdate(id, "form2", sampleFormData2Json))
        insertCheck2.updateType shouldBe a [Updated[_]]

        val original = await(repository.findById(id)).get
        original.atomicId.get shouldBe a [BSONObjectID]
        original.data.get \ "form1" shouldBe sampleFormData1Json
        original.data.get \ "form2" shouldBe sampleFormData2Json

        val updateCheck = await(repository.createOrUpdate(id, "form1", Json.parse("{}")))
        updateCheck.updateType shouldBe a [Updated[_]]

        val updated = await(repository.findById(id)).get
        updated.atomicId.get shouldBe a [BSONObjectID]

        (updated.data.get \ "form1").asOpt[String] shouldBe None
        updated.data.get \ "form2" shouldBe sampleFormData2Json
      }
    }


    "delta update " should {
      "demonstrate how mongo-caching clients (i.e. KeyStore) can accommodate delta updates for free" in new TestSetup {

        val repository = repo("updateDataByKey")

        val id: Id = "updateMeId"

        await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        val original = await(repository.findById(id)).get

        await(repository.createOrUpdate(id, "form2", sampleFormData2Json))
        val updated = await(repository.findById(id)).get

        updated.id shouldBe original.id
        updated.modifiedDetails.createdAt shouldBe original.modifiedDetails.createdAt
        updated.modifiedDetails.lastUpdated should not be original.modifiedDetails.lastUpdated

        updated.data.get \ "form1" shouldBe sampleFormData1Json
        updated.data.get \ "form2" shouldBe sampleFormData2Json

        // Apply the delta change!
        await(repository.createOrUpdate(id, "form2", sampleFormData2JsonDeltaOnlyUsername))

        val updated2 = await(repository.findById(id)).get
        updated2.data.get \ "form1" shouldBe sampleFormData1Json
        updated2.data.get \ "form2" shouldBe sampleFormData2JsonA
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
    lazy val sampleFormData1Json: JsValue = Json.parse( """{
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
