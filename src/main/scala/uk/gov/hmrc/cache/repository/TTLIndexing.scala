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

package uk.gov.hmrc.cache.repository

import play.api.Logger
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONInteger, BSONLong, BSONString}
import reactivemongo.core.commands.{BSONCommandResultMaker, Command, CommandError}
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

trait TTLIndexing[A] {
  self: ReactiveRepository[A, Id] =>

  val expireAfterSeconds: Long

  private lazy val LastUpdatedIndex      = "lastUpdatedIndex"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    import reactivemongo.bson.DefaultBSONHandlers._

    val indexes = collection.indexesManager.list()
    indexes.flatMap { idxs =>
      val idxToUpdate = idxs.find(index =>
        index.eventualName == LastUpdatedIndex
          && index.options.getAs[BSONLong](OptExpireAfterSeconds).fold(false)(_.as[Long] != expireAfterSeconds))

      idxToUpdate.fold(ensureLastUpdated){ index =>
        collection.indexesManager.drop(index.eventualName).flatMap(_ => ensureLastUpdated)
      }
    }
    Logger.info(s"Creating time to live for entries in ${collection.name} to $expireAfterSeconds seconds")
    ensureLastUpdated
  }

  private def ensureLastUpdated(implicit ec: ExecutionContext) = {
    Future.sequence(Seq(collection.indexesManager.ensure(
      Index(
        key     = Seq("modifiedDetails.lastUpdated" -> IndexType.Ascending),
        name    = Some(LastUpdatedIndex),
        options = BSONDocument(OptExpireAfterSeconds -> expireAfterSeconds)
      )
    )))
  }
}


/**
 * 20-May-2014
 * The following code is a workaround for a bug we found in reactivemongo indexesManager.delete() method.
 * The issue only happens with the current version of mongodb (2.6.1) but not with
 * the version the we have on higher environments including production (2.4.8). The patch is intentionally left here
 * so that it can be applied again if mongodb is upgraded and the bug is not fixed in reactivemongo. In which case, the call to
 * indexesManager.delete() in the ensureIndexes() method of this class should be replaced with the following:
 *
 * deleted <- collection.db.command(DeleteIndex(collection.name, idxToUpdate.get.eventualName))
 */
sealed case class DeleteIndex(
                               collection: String,
                               index: String) extends Command[Int] {
  override def makeDocuments = BSONDocument(
    "deleteIndexes" -> BSONString(collection),
    "index" -> BSONString(index))

  object ResultMaker extends BSONCommandResultMaker[Int] {
    def apply(document: BSONDocument) =
      CommandError.checkOk(document, Some("deleteIndexes")).toLeft(document.getAs[BSONInteger]("nIndexesWas").map(_.value).get)
  }

}
