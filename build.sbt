inThisBuild(
  List(
    version ~= { dynVer =>
      if (isTravisTag) dynVer
      else dynVer + "-SNAPSHOT"
    },
    resolvers += Resolver.sonatypeRepo("releases"),
    scalaVersion := "2.12.6",
    publishArtifact in packageDoc := sys.env.contains("CI"),
    publishArtifact in packageSrc := sys.env.contains("CI")
  )
)
onLoadMessage := s"Welcome to sbt-scalafmt ${version.value}"

lazy val plugin = project
  .settings(
    moduleName := "sbt-scalafmt",
    libraryDependencies ++= List(
      // depend on fatjar module with shaded dependencies to avoid classpath conflicts.
      "com.geirsson" %% "scalafmt-big" % {
        val buildVersion = version.in(ThisBuild).value
        if (isTravisTag) {
          println(s"Automatically picking scalafmt version ${buildVersion}")
          buildVersion
        } else {
          "1.6.0-RC3"
        }
      }
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
