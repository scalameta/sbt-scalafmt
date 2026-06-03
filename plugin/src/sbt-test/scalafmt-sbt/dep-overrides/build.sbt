inThisBuild(
  Seq(
    scalaVersion := "2.13.18",
    dependencyOverrides += "org.scala-lang" % "scala-library" % "2.13.18",
    excludeDependencies += "org.scala-lang" % "scala-library",
  )
)

lazy val root = (project in file(".")).settings(
  name := "dep-overrides"
)
