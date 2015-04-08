import sbt._
import sbt.Keys._

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings
  import DefaultBuildSettings._
  import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt}

  val appName = "mongo-caching"
  val appVersion = "0.5.0-SNAPSHOT"

  lazy val mongoCache = Project(appName, file("."))
    .settings(version := appVersion)
    .settings(scalaSettings : _*)
    .settings(defaultSettings() : _*)
    .settings(
      targetJvm := "jvm-1.7",
      shellPrompt := ShellPrompt(appVersion),
      libraryDependencies ++= AppDependencies(),
      resolvers := Seq(
        Opts.resolver.sonatypeReleases
      ),
      crossScalaVersions := Seq("2.11.6")
    )
    .settings(SbtBuildInfo(): _*)

}

private object AppDependencies {

  private val playReactivemongoVersion = "3.2.0"
  private val simpleReactivemongoVersion = "2.1.2"

  val compile = Seq(
    "com.typesafe.play" %% "play" % "2.387" % "provided",

    "uk.gov.hmrc" %% "time" % "1.1.0",

    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion,
    "uk.gov.hmrc" %% "http-exceptions" % "0.2.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % "2.2.4" % scope,
        "com.typesafe.play" %% "play-test" % "2.3.8" % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,

        "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion % scope classifier "tests"
      )
    }.test
  }

  def apply() = compile ++ Test()
}
