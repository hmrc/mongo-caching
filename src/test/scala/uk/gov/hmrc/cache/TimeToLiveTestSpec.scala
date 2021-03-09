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

package uk.gov.hmrc.cache

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import com.typesafe.config.{Config, ConfigFactory}

class TimeToLiveTestSpec extends AnyWordSpecLike with Matchers {

  "TimeToLive" should {
    "yield the default value when there is no config to read " in {
      val ttl = new TimeToLive {}
      ttl.defaultExpireAfter shouldBe 300
    }

    "read the 'cache.expiryInMinutes' config value " in {
      val ttl = new TimeToLive {
        override private[cache] def config: Config = ConfigFactory.parseString("cache.expiryInMinutes: 6")
      }
      ttl.defaultExpireAfter shouldBe 360
    }
  }
}
