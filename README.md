# mongo-caching

[![Build Status](https://travis-ci.org/hmrc/mongo-caching.svg?branch=master)](https://travis-ci.org/hmrc/mongo-caching) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mongo-caching/images/download.svg) ](https://bintray.com/hmrc/releases/mongo-caching/_latestVersion)

Micro-library containing functionality to cache generic data HTTP payloads into MongoDB

## Installing

Include the following dependency in your SBT build

``` scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")

libraryDependencies += "uk.gov.hmrc" %% "mongo-caching" % "[INSERT_VERSION]"
```

For services with `microservice-bootstrap` use version < 7.0
For Java 7 use a version <= 0.7.1

## Configuration

### Custom default expire time
in `application.conf` key `cache.expire` describe cache duration. 
For more syntax please visit [HOCON duration](https://github.com/lightbend/config/blob/master/HOCON.md#duration-format)
```
cache.exipre = 6 weeks
```

## Migrations

### Changes in 7.x line

#### `CacheRepository#apply` is depreacated now - use `CacheRepositoryFactory` 
`CacheRepository#apply` use  global `MongoComponent` which depends on `Play` global. 

##### Before
```scala
class MyController extends Controller { =>

  val formCache = CacheRepository("form", defaultExpireAfter, Cache.mongoFormats) 
  
}
```

##### After
```scala
class MyController @Inject()(factory: CacheRepositoryFactory) extends Controller { =>

  val formCache = factory.create("form") 
  
}
```

#### Configuration key `cache.expiryInMinutes` now become `cache.expiry`

On startup application would log warning if old configuration is still in use

##### Before 

```hocon
cache.expiryInMinutes = 5
```

##### After

```hocon
cache.expiry = 5 minutes
```

#### Migration `CachingController` for play 2.6

Caching controller require only `CachingRepositoryFactory` - it is provided through DI.

```scala
class ExampleController @Inject(cc: ControllerComponents,
                                val cacheRepositoryFactory: CacheRepositoryFactory) extends AbstractController(cc) with CachingController {

}
```

#### Migration `CachingController` for play 2.5

Caching controller require only `CachingRepositoryFactory` and `ExecutionContext` both are provided via DI.

```scala
class ExampleController @Inject(cc: ControllerComponents,
                                val cacheRepositoryFactory: CacheRepositoryFactory,
                                val executionContext: ExecutionContext) extends Controller with CachingController {

}
```

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



## License ##
 
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").


