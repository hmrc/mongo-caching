import sbt.Keys._
import sbt._
import uk.gov.hmrc.versioning.SbtGitVersioning

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings._
  import uk.gov.hmrc.SbtAutoBuildPlugin

  val appName = "mongo-caching"

  lazy val mongoCache = Project(appName, file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(
      targetJvm := "jvm-1.7",
      scalaVersion := "2.11.7",
      libraryDependencies ++= AppDependencies(),
      resolvers := Seq(
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("hmrc", "releases")
      ),
      crossScalaVersions := Seq("2.11.7"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
    )
}

private object AppDependencies {

  import play.core.PlayVersion

  private val playReactivemongoVersion = "4.0.0"
  private val simpleReactivemongoVersion = "3.0.0"

  val compile = Seq(
    "com.typesafe.play" %% "play" % PlayVersion.current % "provided",
    
    "uk.gov.hmrc" %% "time" % "1.4.0",

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
        "uk.gov.hmrc" %% "reactivemongo-test" % "0.2.0" % scope,
        "org.scalatest" %% "scalatest" % "2.2.4" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope
      )
    }.test
  }

  def apply() = compile ++ Test()
}
