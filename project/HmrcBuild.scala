import sbt._
import sbt.Keys._

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings
  import DefaultBuildSettings._
  import BuildDependencies._
  import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt}

  val appName = "mongo-caching"
  val appVersion = "0.4.0-SNAPSHOT"

  lazy val mongoCache = Project(appName, file("."))
    .settings(version := appVersion)
    .settings(scalaSettings : _*)
    .settings(defaultSettings() : _*)
    .settings(
      targetJvm := "jvm-1.7",
      shellPrompt := ShellPrompt(appVersion),
      libraryDependencies ++= AppDependencies(),
      resolvers := Seq(
        Opts.resolver.sonatypeReleases,
        Opts.resolver.sonatypeSnapshots,
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
        "typesafe-snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"
      ),
      crossScalaVersions := Seq("2.11.5", "2.11.4")
    )
    .settings(SbtBuildInfo(): _*)
    .settings(SonatypeBuild(): _*)

}

private object AppDependencies {

  private val playReactivemongoVersion = "3.2.0"
  private val simpleReactivemongoVersion = "2.4.0-SNAPSHOT" //"2.1.2" // TODO...RELEASE VERSION FIRST!!!

  val compile = Seq(
    "com.typesafe.play" %% "play" % "2.3.7" % "provided",

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
        "org.scalatest" %% "scalatest" % "2.2.1" % scope,
        "com.typesafe.play" %% "play-test" % "2.3.3" % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,

        "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion % scope classifier "tests"
      )
    }.test
  }

  def apply() = compile ++ Test()
}

object SonatypeBuild {

  import xerial.sbt.Sonatype._

  def apply() = {
    sonatypeSettings ++ Seq(
      pomExtra := (<url>https://www.gov.uk/government/organisations/hm-revenue-customs</url>
        <licenses>
          <license>
            <name>Apache 2</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          </license>
        </licenses>
        <scm>
          <connection>scm:git@github.com:hmrc/mongo-cache.git</connection>
          <developerConnection>scm:git@github.com:hmrc/mongo-cache.git</developerConnection>
          <url>git@github.com:hmrc/mongo-cache.git</url>
        </scm>
        <developers>
          <developer>
            <id>duncancrawford</id>
            <name>Duncan Crawford</name>
            <url>http://www.equalexperts.com</url>
          </developer>
          <developer>
            <id>jakobgrunig</id>
            <name>Jakob Grunig</name>
            <url>http://www.equalexperts.com</url>
          </developer>
        </developers>)
    )
  }
}
