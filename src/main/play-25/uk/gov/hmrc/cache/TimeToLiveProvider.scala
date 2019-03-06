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

import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Provider}
import play.api.{Configuration, Logger}

import scala.concurrent.duration.{Duration, MINUTES}

class TimeToLiveProvider @Inject()(configuration: Configuration) extends Provider[TimeToLive]{
  override val get: TimeToLive = {
    val oldConfiguration = configuration.getLong("cache.expiryInMinutes")
      .map( value => Duration(value, MINUTES) )
    oldConfiguration.foreach { _ =>
      Logger.warn(
        """Application use `cache.expiryInMinutes` deprecated in mongo-caching 7.x please migrate configuration according to README"""
      )
    }
    val newKey = "cache.expiry"
    val newConfiguration = Duration(configuration.getMilliseconds(newKey).get, TimeUnit.MILLISECONDS)
    if(oldConfiguration.isEmpty && newConfiguration.toMinutes == 0){
      throw new Exception(s"Please use unit for `$newKey` - expire time is less then one second")
    }
    TimeToLive(oldConfiguration.getOrElse(newConfiguration))
  }
}
