def parseTagVersion: String = {
  import scala.sys.process._
  // drop `v` prefix
  "git describe --abbrev=0 --tags".!!.drop(1).trim
}

inThisBuild(List(
  version ~= { dynVer =>
    if (System.getenv("CI") != null) dynVer else s"$parseTagVersion-SNAPSHOT" // only for local publishing
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
  resolvers ++= Resolver.sonatypeOssRepos("public"),
  scalaVersion := "2.13.14",
  packageDoc / publishArtifact := sys.env.contains("CI"),
  packageSrc / publishArtifact := sys.env.contains("CI"),
))
publish / skip := true

val scalafmtVersion = "3.8.1"
onLoadMessage :=
  s"Welcome to sbt-scalafmt ${version.value} (scalafmt $scalafmtVersion)"

lazy val plugin = project.enablePlugins(SbtPlugin).settings(
  moduleName := "sbt-scalafmt",
  libraryDependencies ++= List(
    "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0",
    "org.scalameta" %% "scalafmt-sysops" % scalafmtVersion,
    "org.scalameta" %% "scalafmt-dynamic" % scalafmtVersion,
  ),
  scriptedBufferLog := false,
  scriptedLaunchOpts += s"-Dplugin.version=${version.value}",
  // For compat reasons we have this in here to ensure we are testing against 1.2.8
  // We honestly probably don't need to, so if this ever causes issues, rip it out.
  pluginCrossBuild / sbtVersion := "1.2.8",
)

// For some reason, it doesn't work if this is defined in globalSettings in PublishPlugin.
inScope(Global)(Seq(
  PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray()),
))
