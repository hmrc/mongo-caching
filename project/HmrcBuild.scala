import sbt._
import sbt.Keys._

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings
  import DefaultBuildSettings._
  import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt}

  val appName = "mongo-caching"
  val appVersion = "0.5.0"

  lazy val mongoCache = Project(appName, file("."))
    .settings(version := appVersion)
    .settings(scalaSettings : _*)
    .settings(defaultSettings() : _*)
    .settings(
      targetJvm := "jvm-1.7",
      shellPrompt := ShellPrompt(appVersion),
      libraryDependencies ++= AppDependencies(),
      resolvers := Seq(
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("hmrc", "releases")
      ),
      crossScalaVersions := Seq("2.11.6"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
    )
    .settings(SbtBuildInfo(): _*)

}

private object AppDependencies {

  import play.core.PlayVersion

  private val playReactivemongoVersion = "3.4.1"
  private val simpleReactivemongoVersion = "2.6.1"

  val compile = Seq(
    "com.typesafe.play" %% "play" % PlayVersion.current % "provided",
    
    "uk.gov.hmrc" %% "time" % "1.2.1",

    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion,
    "uk.gov.hmrc" %% "http-exceptions" % "0.4.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % "2.2.4" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,

        "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion % scope classifier "tests"
      )
    }.test
  }

  def apply() = compile ++ Test()
}
