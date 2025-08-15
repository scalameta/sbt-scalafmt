package org.scalafmt.sbt

import sbt.*

object ScalafmtPluginConfigurations {
  val supported = Seq(Compile, Test)
}
