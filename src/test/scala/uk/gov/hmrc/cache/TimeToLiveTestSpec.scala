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

package uk.gov.hmrc.cache

import org.scalatest.Matchers._
import org.scalatest.WordSpecLike
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._

class TimeToLiveTestSpec extends WordSpecLike {

  "TimeToLive" should {
    val conf = Map("cache.expiryInMinutes" -> "6")

    "yield the default value when there is no config to read " in {
      running(new GuiceApplicationBuilder().build()) {
        val ttl = new TimeToLive {}
        ttl.defaultExpireAfter should be(300)
      }
    }

    "read the 'cache.expiryInMinutes' config value " in {
      running(new GuiceApplicationBuilder().configure(conf).build()) {
        val ttl = new TimeToLive {}
        ttl.defaultExpireAfter should be(360)
      }
    }

    "yield the default value when 'cache.expiryInMinutes' config is missing" in {
      running(new GuiceApplicationBuilder().build()) {
        val ttl = new TimeToLive {}
        ttl.defaultExpireAfter should be(300)
      }
    }
  }
}
