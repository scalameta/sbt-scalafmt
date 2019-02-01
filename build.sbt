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
    resolvers += Resolver.sonatypeRepo("snapshots"),
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
    libraryDependencies ++= {
      val scalafmtVersion: String = {
        if (CiReleasePlugin.isTravisTag) {
          val buildVersion = version.in(ThisBuild).value

          println(
            s"Automatically picking scalafmt version $buildVersion. TRAVIS_TAG=${System.getenv("TRAVIS_TAG")}"
          )
          buildVersion
        } else {
          version.value
        }
      }

      List(
        "org.scalameta" %% "scalafmt-core" % scalafmtVersion,
        "org.scalameta" %% "scalafmt-cli" % scalafmtVersion
      )
    },
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
