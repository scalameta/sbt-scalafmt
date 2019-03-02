package org.scalafmt.sbt

import java.io.PrintWriter
import java.nio.file.Path

import org.scalafmt.interfaces.ScalafmtReporter
import sbt.internal.util.MessageOnlyException
import sbt.util.Logger

class ScalafmtSbtReporter(log: Logger, writer: PrintWriter)
    extends ScalafmtReporter {
  override def error(file: Path, message: String): Unit = {
    throw new MessageOnlyException(s"$message: $file")
  }

  override def error(file: Path, e: Throwable): Unit =
    error(file, e.getMessage)

  override def excluded(file: Path): Unit =
    log.debug(s"file excluded: $file")

  override def parsedConfig(config: Path, scalafmtVersion: String): Unit =
    log.debug(s"parsed config (v$scalafmtVersion): $config")

  override def downloadWriter(): PrintWriter = writer
}
