import sbt.Keys._
import sbt._

val libName = "mongo-caching"

val play25 = "2.5.19"

val play26 = "2.6.20"

val compileDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "uk.gov.hmrc"       %% "time"               % "3.3.0"
  ),
  play25 = Seq(
    "com.typesafe.play" %% "play"                 % play25 % "provided",
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.14.0-play-25",
    "uk.gov.hmrc"       %% "http-verbs"           % "9.3.0-play-25"
  ),
  play26 = Seq(
    "com.typesafe.play" %% "play"                 % play26 % "provided",
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.14.0-play-26",
    "uk.gov.hmrc"       %% "http-verbs"           % "9.3.0-play-26"
  )
)


val testDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "org.scalatest"     %% "scalatest"              % "3.0.5"             % "test",
    "org.pegdown"       % "pegdown"                 % "1.4.2"             % "test"
  ),
  play25 = Seq(
    "com.typesafe.play"      %% "play-test"          % play25              % "test",
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.9.0-play-25"     % "test"
  ),
  play26 = Seq(
    "com.typesafe.play"      %% "play-test"          % play26              % "test",
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.9.0-play-26"     % "test"
  )
)


lazy val mongoCache = Project(libName, file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
    .settings(
      majorVersion := 6,
      makePublicallyAvailableOnBintray := true,
      scalaVersion := "2.11.12",
      libraryDependencies ++= compileDependencies ++ testDependencies,
      resolvers := Seq(
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.jcenterRepo
      ),
      crossScalaVersions := Seq("2.11.12", "2.12.8")
    ).settings(PlayCrossCompilation.playCrossCompilationSettings)
