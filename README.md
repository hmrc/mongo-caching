# mongo-caching

[![Build Status](https://travis-ci.org/hmrc/mongo-caching.svg?branch=master)](https://travis-ci.org/hmrc/mongo-caching) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mongo-caching/images/download.svg) ](https://bintray.com/hmrc/releases/mongo-caching/_latestVersion)

Micro-library containing functionality to cache generic data HTTP payloads into MongoDB

## Installing

Include the following dependency in your SBT build

``` scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")

libraryDependencies += "uk.gov.hmrc" %% "mongo-caching" % "[INSERT_VERSION]"
```
For Java 7 use a version <= 0.7.1

## Migrations

### Changes in 6.x line

#### Trait `TimeToLive` is now case class for injecting

Play 2.6 encourage to use DI instead inheritance. Default values are provided via injection rather then inherit traits.

##### Before

```scala
trait CachingController extends MongoDbConnection with TimeToLive 
```
##### After
In trait example 
```scala
trait CachingController {
  def expiredAfter: TimeToLive
}
```
or class example
```scala
class CachingController @Inject()(val expiredAfter: TimeToLive) {
  // Implementation 
}
```

##### Extending `CachingControllerExample`
```scala
class MyController extends CachingController with TimeToLive {
  // Implementation 
}
```

is now

```scala
class MyController @Inject()(val expiredAfter: TimeToLive) extends CachingController {
  // Implementation 
}
````

Every usage of caching controller require changes from extending `TimeToLive` to injected paramters like above.

####

#### Trait `TTLIndexing` now use `expireAfter: TimeToLive` instead `expiresAfterSeconds: Long`

Using specialised type is less error prone and makes usage of DI easier. This change affect only code which
have custom implementation of `CacheRepository`. 

##### Before

```scala
class CacheMongoRepository(collName: String,
                           override val expireAfterSeconds: Long,
                           cacheFormats: Format[Cache] = Cache.mongoFormats)(implicit mongo: () => DB, ec: ExecutionContext)
```

##### After
```scala
class CacheMongoRepository(collName: String,
                           override val expireAfter: TimeToLive,
                           cacheFormats: Format[Cache] = Cache.mongoFormats)(implicit mongo: () => DB, ec: ExecutionContext)
```

#### 


## License ##
 
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").


