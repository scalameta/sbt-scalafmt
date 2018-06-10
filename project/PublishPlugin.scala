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
      System.getenv("TRAVIS_TAG") != null
    def isTravisSecure: Boolean =
      System.getenv("TRAVIS_SECURE_ENV_VARS") == "true"
  }

  import autoImport._

  private def env(key: String): String =
    Option(System.getenv(key)).getOrElse {
      throw new NoSuchElementException(key)
    }

  override def globalSettings: Seq[Def.Setting[_]] = List(
    organization := "com.geirsson",
    homepage := Some(url("https://github.com/scalameta/sbt-scalafmt")),
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
    publishMavenStyle := true,
    commands += Command.command("ci-release") { s =>
      val logger = streams.value.log
      logger.info(
        s"Running ci-release.\n" +
          s"  TRAVIS_SECURE_ENV_VARS=${env("TRAVIS_SECURE_ENV_VARS")}\n" +
          s"  TRAVIS_BRANCH=${env("TRAVIS_BRANCH")}\n" +
          s"  TRAVIS_TAG=${env("TRAVIS_TAG")}"
      )
      "git log HEAD~2..HEAD".!

      if (!isTravisSecure) {
        logger.info("No access to secret variables, doing nothing")
        s
      } else {
        logger.info("Setting up gpg")
        (s"echo ${env("PGP_SECRET")}" #| "base64 --decode" #| "gpg --import").!
        if (!isTravisTag) {
          logger.info(s"No tag push, publishing SNAPSHOT")
          "+publish" ::
            s
        } else {
          logger.info("Tag push detected, publishing a stable release")
          "+publishSigned" ::
            "sonatypeReleaseAll" ::
            s
        }
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
