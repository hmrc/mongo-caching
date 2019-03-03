/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.Logger
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.cache.TimeToLive
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

trait TTLIndexing[A] { self: ReactiveRepository[A, Id] =>

  val expireAfter: TimeToLive

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    import reactivemongo.bson.DefaultBSONHandlers._
    val indexes = collection.indexesManager.list()
    indexes.flatMap { indexList =>
      val idxToUpdate = indexList.find { index =>
        val indexExpirationTime = index.options.getAs[Long](TTLIndexing.optExpireAfterSeconds)
        index.eventualName == TTLIndexing.lastUpdatedIndexName && !indexExpirationTime.contains(expireAfter.inSeconds)
      }
      Logger.info(s"Creating time to live index for entries in ${collection.name} to $expireAfter seconds")
      idxToUpdate.fold[Future[Seq[Boolean]]](ensureLastUpdated) { index =>
        collection.indexesManager.drop(index.eventualName).flatMap(_ => ensureLastUpdated)
      }.andThen {
        case _ => Logger.info(s"Time to live indexes for entries in ${collection.name} created")
      }
    }
  }

  private def ensureLastUpdated(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    collection.indexesManager.ensure(
      Index(
        key = Seq("modifiedDetails.lastUpdated" -> IndexType.Ascending),
        name = Some(TTLIndexing.lastUpdatedIndexName),
        options = BSONDocument(TTLIndexing.optExpireAfterSeconds -> expireAfter.inSeconds)
      )
    ).map(Seq(_))
  }
}

object TTLIndexing {
  private val lastUpdatedIndexName = "lastUpdatedIndex"
  private val optExpireAfterSeconds = "expireAfterSeconds"
}