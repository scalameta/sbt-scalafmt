import sbt.Def
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sys.process._

object PublishPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin
  object autoImport {
    def isTravisTag: Boolean =
      System.getProperty("TRAVIS_TAG") != null
    def isTravisSecure: Boolean =
      System.getProperty("TRAVIS_SECURE_ENV_VARS") == "true"
  }
  import autoImport._

  override def globalSettings: Seq[Def.Setting[_]] = List(
    organization := "com.geirsson",
    homepage := Some(url("https://github.com/scalameta/sbt-scalafmt")),
    publishMavenStyle := true,
    licenses := Seq(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "olafurpg",
        "Ólafur Páll Geirsson",
        "olafurpg@gmail.com",
        url("https://geirsson.com")
      )
    ),
    commands += Command.command("ci-release") { s =>
      if (!isTravisSecure) {
        println(s"Skipping publish, branch=${sys.env.get("TRAVIS_BRANCH")}")
        s
      } else {
        println("Setting up gpg")
        "git log HEAD~2..HEAD".!
        (s"echo ${sys.env("PGP_SECRET")}" #| "base64 --decode" #| "gpg --import").!
        println("Publishing release")
        "+publishSigned" ::
          "sonatypeReleaseAll" ::
          s
      }
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] = List(
    publishTo := Some {
      if (isTravisTag) Opts.resolver.sonatypeStaging
      else Opts.resolver.sonatypeSnapshots
    }
  )

}
