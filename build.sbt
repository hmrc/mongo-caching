import sbt.Keys._
import sbt._

val libName = "mongo-caching"

val play26Version = "2.6.25"
val play27Version = "2.7.9"
val play28Version = "2.8.7"

val compileDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "uk.gov.hmrc"       %% "time"               % "3.8.0"
  ),
  play26 = Seq(
    "com.typesafe.play" %% "play"                 % play26Version,
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "8.0.0-play-26",
    "uk.gov.hmrc"       %% "http-verbs-play-26"   % "13.1.0"
  ),
  play27 = Seq(
    "com.typesafe.play" %% "play"                 % play27Version,
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "8.0.0-play-27",
    "uk.gov.hmrc"       %% "http-verbs-play-27"   % "13.1.0"
  ),
  play28 = Seq(
    "com.typesafe.play" %% "play"                 % play28Version,
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "8.0.0-play-28",
    "uk.gov.hmrc"       %% "http-verbs-play-28"   % "13.1.0"
  )
)


val testDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "org.scalatest"        %% "scalatest"                % "3.1.4"       % Test,
    "com.vladsch.flexmark" %  "flexmark-all"             % "0.36.8"      % Test,
    "org.scalatestplus"    %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test
  ),
  play26 = Seq(
    "com.typesafe.play"      %% "play-test"          % play26Version     % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "5.0.0-play-26"   % Test
  ),
  play27 = Seq(
    "com.typesafe.play"      %% "play-test"          % play27Version     % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "5.0.0-play-27"   % Test
  ),
  play28 = Seq(
    "com.typesafe.play"      %% "play-test"          % play28Version     % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "5.0.0-play-28"   % Test
  )
)


lazy val mongoCache = Project(libName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    majorVersion := 7,
    makePublicallyAvailableOnBintray := true,
    scalaVersion := "2.12.13",
    libraryDependencies ++= compileDependencies ++ testDependencies,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    )
  ).settings(PlayCrossCompilation.playCrossCompilationSettings)
