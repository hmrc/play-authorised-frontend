import sbt.Keys._
import sbt._
import uk.gov.hmrc.{SbtArtifactory, SbtAutoBuildPlugin}
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion


object HmrcBuild extends Build {

  val appName = "play-authorised-frontend"

  lazy val library = (project in file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
    .settings(
      name := appName,
      scalaVersion := "2.11.7",
      crossScalaVersions := Seq("2.11.7"),
      libraryDependencies ++= AppDependencies()
    )
    .settings(majorVersion := 7)
    .settings(resolvers += Resolver.bintrayRepo("hmrc", "releases"),
      resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
    )
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile = Seq(
    "com.typesafe.play" %% "play" % PlayVersion.current % "provided",
    json % "provided",
    "uk.gov.hmrc" %% "http-verbs" % "8.10.0-play-25",
    "uk.gov.hmrc" %% "domain" % "4.1.0",
    "uk.gov.hmrc" %% "time" % "3.2.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.scalatest" %% "scalatest" % "2.2.4" % scope,
        "org.pegdown" % "pegdown" % "1.5.0" % scope,
        "com.github.tomakehurst" % "wiremock" % "1.54" % scope excludeAll ExclusionRule(organization = "org.apache.httpcomponents"),
        "uk.gov.hmrc" %% "hmrctest" % "3.3.0" % scope,
        "uk.gov.hmrc" %% "http-verbs-test" % "1.1.0" % scope,
        "org.mockito" % "mockito-all" % "1.9.5" % "test"
      )
    }.test
  }

  def apply() = compile ++ Test()
}


object Collaborators {

  def apply() = {
    pomExtra := <url>https://www.gov.uk/government/organisations/hm-revenue-customs</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git@github.com:hmrc/play-authorised-frontend.git</connection>
        <developerConnection>scm:git@github.com:hmrc/play-authorised-frontend.git</developerConnection>
        <url>git@github.com:hmrc/play-authorised-frontend.git</url>
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
        <developer>
          <id>xnejp03</id>
          <name>Petr Nejedly</name>
          <url>http://www.equalexperts.com</url>
        </developer>
        <developer>
          <id>alvarovilaplana</id>
          <name>Alvaro Vilaplana</name>
          <url>http://www.equalexperts.com</url>
        </developer>
        <developer>
          <id>vaughansharman</id>
          <name>Vaughan Sharman</name>
          <url>http://www.equalexperts.com</url>
        </developer>
        <developer>
          <id>davesammut</id>
          <name>Dave Sammut</name>
          <url>http://www.equalexperts.com</url>
        </developer>
      </developers>
  }
}

