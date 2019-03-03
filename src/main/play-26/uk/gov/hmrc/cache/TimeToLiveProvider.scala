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

package uk.gov.hmrc.cache

import javax.inject.{Inject, Provider}
import play.api.Configuration
import play.api.Logger

import scala.concurrent.duration.{Duration, MINUTES}

class TimeToLiveProvider @Inject()(configuration: Configuration) extends Provider[TimeToLive]{

  /**
    * Compatibility layer with old configuration if cache
    */
  override val get: TimeToLive = {
    val oldConfiguration = configuration.get[Option[Long]]("cache.expiryInMinutes")
      .map(value => Duration(value, MINUTES))
    oldConfiguration.foreach { _ =>
      Logger.warn(
        """Application use `cache.expiryInMinutes` deprecated in 6.x line -
          |please migrate configuration according to mongo-caching README""".stripMargin
      )
    }
    val newConfiguration = configuration.get[Duration]("cache.expiry")
    TimeToLive(oldConfiguration.getOrElse(newConfiguration))
  }
}