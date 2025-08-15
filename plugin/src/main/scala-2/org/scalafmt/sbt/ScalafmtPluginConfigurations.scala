package org.scalafmt.sbt

import sbt._

object ScalafmtPluginConfigurations {
  val supported = Seq(Compile, Test, IntegrationTest)
}
