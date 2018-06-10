import com.typesafe.sbt.pgp.PgpSettings

inThisBuild(
  List(
    version ~= { dynVer =>
      if (sys.env.contains("TRAVIS_TAG")) dynVer
      else dynVer + "-SNAPSHOT"
    },
    scalaVersion := "2.12.6",
    // faster publishLocal:
    publishArtifact in packageDoc := sys.env.contains("CI"),
    publishArtifact in packageSrc := sys.env.contains("CI"),
  )
)

lazy val shading =
  inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings) ++
    // Why does this have to be repeated here?
    // Can't figure out why configuration gets lost without this in particular...
    _root_.coursier.ShadingPlugin.projectSettings ++
    Seq(
      shadingNamespace := "org.scalafmt.shaded",
      publish := publish.in(Shading).value,
      publishLocal := publishLocal.in(Shading).value,
      PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value,
      PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
    )

lazy val plugin = project
  .settings(
    shading,
    moduleName := "sbt-scalafmt",
    libraryDependencies += "com.geirsson" %% "scalafmt-cli" % "1.6.0-RC2" % "shaded",
    sbtPlugin := true,
    scriptedBufferLog := false,
    scriptedLaunchOpts += s"-Dplugin.version=${version.value}"
  )
  .enablePlugins(ShadingPlugin)

// For some reason, it doesn't work if this is defined in globalSettings in PublishPlugin.
inScope(Global)(
  Seq(
    PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray())
  )
)
