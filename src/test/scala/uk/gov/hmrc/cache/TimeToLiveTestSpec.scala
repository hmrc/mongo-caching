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

import org.scalatest.Matchers._
import org.scalatest.WordSpecLike
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._

class TimeToLiveTestSpec extends WordSpecLike {

  "TimeToLive" should {
    "yield the default value when there is no config to read " in {
      running() { app =>
        val ttl = app.injector.instanceOf[TimeToLive]
        ttl.inSeconds should be(300)
      }
    }

    "read the 'cache.expiryInMinutes' config value " in {
      val oldConf = Configuration("cache.expiryInMinutes" -> "6")
      running(_.configure(oldConf)) { app =>
        val ttl = app.injector.instanceOf[TimeToLive]
        ttl.inSeconds should be(360)
      }
    }

    "read the 'cache.expiry' config value " in {
      val newConf = Configuration("cache.expiry" -> "6 minutes")
      running(_.configure(newConf)) { app =>
        val ttl = app.injector.instanceOf[TimeToLive]
        ttl.inSeconds should be(360)
      }
    }
  }
}
