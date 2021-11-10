package org.scalafmt.sbt

import java.nio.file.Path

import org.scalafmt.interfaces.{Scalafmt, ScalafmtSession}

private[sbt] class CompatibilityScalafmtSession(
    config: Path,
    instance: Scalafmt
) extends ScalafmtSession {
  override def format(file: Path, code: String): String =
    instance.format(config, file, code)
  override def matchesProjectFilters(file: Path): Boolean = true
}
