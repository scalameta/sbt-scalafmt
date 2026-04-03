import scala.util.Properties.isJavaAtLeast

val scalafmtVersion = "3.11.0"
val scala2 = "2.12.21"
val scala3 = "3.8.3"

addCommandAlias("test2", s"++$scala2 plugin/scripted")
addCommandAlias("test3", s"++$scala3 plugin/scripted")

inThisBuild(List(
  // version is set dynamically by sbt-dynver, but let's adjust it
  version := {
    val curVersion = version.value
    def dynVer(out: sbtdynver.GitDescribeOutput): String = {
      def tagVersion = out.ref.dropPrefix
      if (out.isCleanAfterTag) tagVersion
      else if (System.getenv("CI") == null) s"$tagVersion-next-SNAPSHOT" // modified for local builds
      else if (out.commitSuffix.distance == 0) tagVersion
      else if (sys.props.contains("backport.release")) tagVersion
      else curVersion
    }
    dynverGitDescribeOutput.value.mkVersion(dynVer, curVersion)
  },
  organization := "org.scalameta",
  homepage := Some(url("https://github.com/scalameta/sbt-scalafmt")),
  licenses :=
    Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "poslegm",
      "Mikhail Chugunkov",
      "poslegm@gmail.com",
      url("https://chugunkov.website/"),
    ),
    Developer(
      "olafurpg",
      "Ólafur Páll Geirsson",
      "olafurpg@gmail.com",
      url("https://geirsson.com"),
    ),
    Developer(
      "tanishiking",
      "Rikito Taniguchi",
      "rikiriki1238@gmail.com",
      url("https://github.com/tanishiking/"),
    ),
  ),
  scalaVersion := scala2,
  crossScalaVersions += scala3,
  packageDoc / publishArtifact := insideCI.value,
  packageSrc / publishArtifact := insideCI.value,
))
publish / skip := true

onLoadMessage :=
  s"Welcome to sbt-scalafmt ${version.value} (scalafmt $scalafmtVersion)"

def isScala3 = Def.setting(scalaBinaryVersion.value == "3")

lazy val plugin = project.enablePlugins(SbtPlugin, ScriptedPlugin).settings(
  moduleName := "sbt-scalafmt",
  libraryDependencies ++= List(
    "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0",
    "org.scalameta" %% "scalafmt-sysops" % scalafmtVersion cross
      CrossVersion.for3Use2_13,
    "org.scalameta" %% "scalafmt-dynamic" % scalafmtVersion cross
      CrossVersion.for3Use2_13,
  ),
  scriptedBufferLog := false,
  scriptedLaunchOpts += s"-Dplugin.version=${version.value}",
  // For compat reasons we have this in here to ensure we are testing against 1.2.8
  // We honestly probably don't need to, so if this ever causes issues, rip it out.
  pluginCrossBuild / sbtVersion := {
    if (!isScala3.value) "1.12.9"
    else if (!isJavaAtLeast("17")) sys.error("Scala 3 requires JDK 17+")
    else "2.0.0-RC11"
  },
  conflictWarning := {
    if (!isScala3.value) conflictWarning.value
    else ConflictWarning("warn", Level.Warn, failOnConflict = false)
  },
  scalacOptions += { if (isScala3.value) "-release:17" else "-release:8" },
)

// For some reason, it doesn't work if this is defined in globalSettings in PublishPlugin.
inScope(Global)(Seq(
  PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray()),
))
