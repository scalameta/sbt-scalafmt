addSbtPlugin(
  "io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version
)
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.2.6")
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
