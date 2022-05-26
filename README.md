[DEPRECATED]
=
*Use https://github.com/hmrc/hmrc-mongo#cache instead*

# mongo-caching

Micro-library containing functionality to cache generic JSON data in MongoDB

## Installing

Include the following dependency in your SBT build

``` scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")

libraryDependencies += "uk.gov.hmrc" %% "mongo-caching" % "[INSERT_VERSION]"
```
For Java 7 use a version <= 0.7.1

## Migration

### 7.x.x

Supports Play 2.6, 2.7, 2.8 (Scala 2.12)

* `CacheController` has been removed. The code (from v6.x.x) can be included in the client service if required.
* The deprecated `CacheRepository` has been removed. Use `CacheMongoRepository` instead.
* `play.allowGlobalApplication = true` is no longer required in your application.conf to use this library.


## Usage

The library comprises two central classes, `CacheMongoRepository`, and `CacheController`.

Most use-cases should use the `CacheMongoRepository` unless you have a concrete need to expose your cache via HTTP. The Controller class wraps the Repository class in a Play Controller, and can be wired directly into a `routes` file.

Example usage of `CacheMongoRepository` in Scala:

```scala
@Singleton
class SessionCacheRepository @Inject()(
  @Named("mongodb.session.expireAfterSeconds") expireAfterSeconds: Int,
  mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
    extends CacheMongoRepository("sessions", expireAfterSeconds)(mongo.mongoConnector.db, ec)
```

This creates a repository class, which can be called from elsewhere in your application. You should only create this class on application start-up, rather than dynamically calling `new`, as the class constructor will check for, and optionally create, database indexes.

The `CacheMongoRepository` class exposes a method `createOrUpdate` that is used to upsert data into the cache.

```scala
def createOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]]
```

Data inserted using this method has a time-to-live (TTL) that applies per `Id`. The amount of time is configured when creating the class; `expireAfterSeconds` in our example. Any modifications of data for an `Id` will reset the TTL.

The JSON structure that is cached in Mongo:

```json
{
	"id": {
		"key1": "value1",
		"key2": "value1"
	}
}
```

This structure allows caching multiple keys against an ID. As cached values expire per ID, this provides a way to expire related data at the same time.

Simpler use-cases for this library can hardcode `key` to a constant, and provide a cache of ID to value.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
