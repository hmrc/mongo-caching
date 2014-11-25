package uk.gov.hmrc.cache

import org.scalatest.Matchers._
import org.scalatest.WordSpecLike
import play.api.test.{FakeApplication, WithApplication}


class TimeToLiveTestSpec extends WordSpecLike {

  "TimeToLive" should {

    val conf = Map("cache.expiryInMinutes" -> "6")
    val fakeApp = FakeApplication(additionalConfiguration = conf)

    "yield the default value when there is no config to read " in new WithApplication {
      val ttl = new TimeToLive {}
      ttl.defaultExpireAfter should be(300)
    }

    "read the 'cache.expiryInMinutes' config value " in new WithApplication(fakeApp) {
      val ttl = new TimeToLive {}
      ttl.defaultExpireAfter should be(360)
    }

    "yield the default value when 'cache.expiryInMinutes' config is missing" in new WithApplication(FakeApplication()) {
      val ttl = new TimeToLive {}
      ttl.defaultExpireAfter should be(300)
    }
  }
}
