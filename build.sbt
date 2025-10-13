inThisBuild(List(
  // version is set dynamically by sbt-dynver, but let's adjust it
  version := {
    val curVersion = version.value
    def dynVer(out: sbtdynver.GitDescribeOutput): String = {
      def tagVersion = out.ref.dropPrefix
      if (out.isCleanAfterTag) tagVersion
      else if (System.getProperty("CI") == null) s"$tagVersion-next-SNAPSHOT" // modified for local builds
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
  scalaVersion := "2.12.20",
  crossScalaVersions += "3.7.3",
  packageDoc / publishArtifact := insideCI.value,
  packageSrc / publishArtifact := insideCI.value,
))
publish / skip := true

val scalafmtVersion = "3.10.0"
onLoadMessage :=
  s"Welcome to sbt-scalafmt ${version.value} (scalafmt $scalafmtVersion)"

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
    scalaBinaryVersion.value match {
      case "2.12" =>
        if (scala.util.Properties.isJavaAtLeast("17")) "1.9.0" else "1.2.8"
      case _ => "2.0.0-RC4"
    }
  },
  conflictWarning := {
    scalaBinaryVersion.value match {
      case "3" => ConflictWarning("warn", Level.Warn, false)
      case _ => conflictWarning.value
    }
  },
)

// For some reason, it doesn't work if this is defined in globalSettings in PublishPlugin.
inScope(Global)(Seq(
  PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray()),
))
