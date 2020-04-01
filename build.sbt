import sbt.Keys._
import sbt._
import uk.gov.hmrc.playcrosscompilation.PlayVersion.Play25

val libName = "mongo-caching"

val play25Version = "2.5.19"
val play26Version = "2.6.23"
val play27Version = "2.7.4"

val compileDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "uk.gov.hmrc"       %% "time"               % "3.8.0"
  ),
  play25 = Seq(
    "com.typesafe.play" %% "play"                 % play25Version    % Provided,
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.26.0-play-25",
    "uk.gov.hmrc"       %% "http-verbs"           % "10.6.0-play-25"
  ),
  play26 = Seq(
    "com.typesafe.play" %% "play"                 % play26Version    % Provided,
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.26.0-play-26",
    "uk.gov.hmrc"       %% "http-verbs"           % "10.6.0-play-26"
  ),
  play27 = Seq(
    "com.typesafe.play" %% "play"                 % play27Version    % Provided,
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.26.0-play-27",
    "uk.gov.hmrc"       %% "http-verbs"           % "10.6.0-play-27"
  )
)


val testDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "org.scalatest"     %% "scalatest"              % "3.0.5"             % Test,
    "org.scalacheck"    %% "scalacheck"             % "1.14.0"            % Test,
    "org.pegdown"       % "pegdown"                 % "1.6.0"             % Test
  ),
  play25 = Seq(
    "com.typesafe.play"      %% "play-test"          % play25Version       % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.19.0-play-25"    % Test
  ),
  play26 = Seq(
    "com.typesafe.play"      %% "play-test"          % play26Version       % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.19.0-play-26"    % Test
  ),
  play27 = Seq(
    "com.typesafe.play"      %% "play-test"          % play27Version       % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.19.0-play-27"    % Test
  )
)


lazy val mongoCache = Project(libName, file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
    .settings(
      majorVersion := 6,
      makePublicallyAvailableOnBintray := true,
      // Only use 2.11 if we're doing a Play25 build, as it does not support 2.12
      scalaVersion := (if(PlayCrossCompilation.playVersion == Play25) "2.11.12" else "2.12.10"),
      crossScalaVersions := Seq("2.11.12", "2.12.10"),
        libraryDependencies ++= compileDependencies ++ testDependencies,
        resolvers := Seq(
          "typesafe-releases" at "https://repo.typesafe.com/typesafe/releases/",
          Resolver.bintrayRepo("hmrc", "releases"),
          Resolver.jcenterRepo
      )
    ).settings(PlayCrossCompilation.playCrossCompilationSettings)
