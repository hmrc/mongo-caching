import PlayCrossCompilation.playCrossCompilationSettings
import sbt.Keys._
import sbt._

val libName = "mongo-caching"

val play25Version = "2.5.12"
val play26Version = "2.6.20"

val compileDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "uk.gov.hmrc"       %% "time"                 % "3.2.0"
  ),
  play25 = Seq(
    "com.typesafe.play" %% "play"                 % play25Version % "provided",
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.3.0-play-25",
    "uk.gov.hmrc"       %% "http-verbs"           % "8.9.0-play-25"
  ),
  play26 = Seq(
    "com.typesafe.play" %% "play"                 % play26Version % "provided",
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.3.0-play-26",
    "uk.gov.hmrc"       %% "http-verbs"           % "8.9.0-play-26"
  )
)

val testDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "org.scalatest"     %% "scalatest"          % "2.2.4"         % "test",
    "org.pegdown"       % "pegdown"             % "1.4.2"         % "test",
    "org.slf4j"         % "slf4j-log4j12"       % "1.7.25"        % "test"
  ),
  play25 = Seq(
    "uk.gov.hmrc"       %% "reactivemongo-test" % "4.0.0-play-25"  % "test",
    "com.typesafe.play" %% "play-test"          % play25Version    % "test"
  ),
  play26 = Seq(
    "uk.gov.hmrc"       %% "reactivemongo-test" % "4.0.0-play-26"  % "test",
    "com.typesafe.play" %% "play-test"          % play26Version    % "test"
  )
)

lazy val mongoCache = Project(libName, file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
    .settings(
      majorVersion := 6,
      makePublicallyAvailableOnBintray := true,
      scalaVersion := "2.11.7",
      libraryDependencies ++= compileDependencies ++ testDependencies,
      evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
      resolvers := Seq(
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.jcenterRepo
      ),
      crossScalaVersions := Seq("2.11.7"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      playCrossCompilationSettings
    )