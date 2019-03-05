inThisBuild(
  List(
    organization := "org.scalameta",
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
    resolvers += Resolver.sonatypeRepo("releases"),
    scalaVersion := "2.12.6",
    publishArtifact in packageDoc := sys.env.contains("CI"),
    publishArtifact in packageSrc := sys.env.contains("CI")
  )
)
onLoadMessage := s"Welcome to sbt-scalafmt ${version.value}"
skip in publish := true

lazy val plugin = project
  .settings(
    moduleName := "sbt-scalafmt",
    libraryDependencies ++= List(
      "org.scalameta" %% "scalafmt-dynamic" % "2.0.0-RC5"
    ),
    sbtPlugin := true,
    scriptedBufferLog := false,
    scriptedLaunchOpts += s"-Dplugin.version=${version.value}"
  )

// For some reason, it doesn't work if this is defined in globalSettings in PublishPlugin.
inScope(Global)(
  Seq(
    PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray())
  )
)
