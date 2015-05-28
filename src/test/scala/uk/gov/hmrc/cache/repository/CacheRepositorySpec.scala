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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Json
import reactivemongo.bson.BSONLong
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo.MongoSpecSupport
import org.scalatest.{WordSpecLike, Matchers}


class CacheRepositorySpec extends WordSpecLike with Matchers with MongoSpecSupport with BeforeAndAfterEach with Eventually {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.{Await, Future}
  import scala.concurrent.duration._

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
    "be created" in new TestSetup {
      val repository = repo("updateDataByKey")

      val id: Id = "createMeId"

      val notFound = await(repository.findById(id))

      notFound shouldBe None

      await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
      val original = await(repository.findById(id)).get

      original.id shouldBe id
      Option(original.modifiedDetails.createdAt) shouldBe defined
      Option(original.modifiedDetails.lastUpdated) shouldBe defined

      original.data.get \ "form1" shouldBe sampleFormData1Json
    }

    "delete" should {
      "be removed" in new TestSetup {
        val repository = repo("delete")

        val id: Id = "deleteMeId"

        await(repository.createOrUpdate(id, "form1", sampleFormData1Json))
        val original = await(repository.findById(id)).get

        original.id shouldBe id
        original.data.get \ "form1" shouldBe sampleFormData1Json

        repository.removeById(id)
        val removed = await(repository.findById(id))
        removed should be(empty)
      }
    }

    "be updated" in new TestSetup {
      val repository = repo("updateDataByKey")

      val id: Id = "updateMeId"

      await(repository.save(Cache(id, Some(Json.obj("form1" -> sampleFormData1Json)))))
      val original = await(repository.findById(id)).get

      await(repository.createOrUpdate(id, "form2", sampleFormData2Json))

      val updated = await(repository.findById(id)).get

      updated.id shouldBe original.id
      updated.modifiedDetails.createdAt shouldBe original.modifiedDetails.createdAt
      updated.modifiedDetails.lastUpdated should not be original.modifiedDetails.lastUpdated

      updated.data.get \ "form1" shouldBe sampleFormData1Json
      updated.data.get \ "form2" shouldBe sampleFormData2Json
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

    //This test is intentionally commented out. Enable it locally to test that the removal of expired documents works.
    "delete a document after it is not updated for longer than the \'expireAfter\' amount of time" ignore new TestSetup {
      val repository = repo("expireDocument", 2)
      val id = Id("expireTestId")
      await(repository.createOrUpdate(id, "form", sampleFormData1Json))
      Thread.sleep(60000)
      val doc = await(repository.findById(id))
      doc shouldBe None
    }
  }


  private trait TestSetup {
    lazy val sampleFormData1Json = Json.parse( """{
                                                 |"form-field-username":"John Densemore",
                                                 |"form-field-password":"password1",
                                                 |"form-field-address-number":42,
                                                 |"form-field-address-one":"The Door"
                                                 |}""".stripMargin)

    lazy val sampleFormData2Json = Json.parse( """{
                                                 |"form-field-username":"Mark Dearnely",
                                                 |"form-field-password":"ihaveaplan",
                                                 |"form-field-address-number": 1,
                                                 |"form-field-address-one":"HMRC Road"
                                                 |}""".stripMargin)
  }

}
