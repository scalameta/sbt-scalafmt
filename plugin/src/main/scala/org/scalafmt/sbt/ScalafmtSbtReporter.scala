package org.scalafmt.sbt

import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.file.Path

import sbt.internal.util.MessageOnlyException
import sbt.util.Logger

import scala.util.control.NoStackTrace

import org.scalafmt.interfaces.ScalafmtReporter

class ScalafmtSbtReporter(
    log: Logger,
    out: OutputStreamWriter,
    detailedErrorEnabled: Boolean
) extends ScalafmtReporter {
  override def error(file: Path, message: String): Unit =
    throw new MessageOnlyException(s"$message: $file")

  override def error(file: Path, e: Throwable): Unit =
    Option(e.getMessage) match {
      case Some(_) if detailedErrorEnabled =>
        throw new ScalafmtSbtError(file, e)
      case Some(_) => error(file, e.getMessage)
      case None => throw new FailedToFormat(file.toString, e)
    }

  override def error(file: Path, message: String, e: Throwable): Unit =
    if (e.getMessage != null) {
      error(file, s"$message: ${e.getMessage()}")
    } else {
      throw new FailedToFormat(file.toString, e)
    }

  override def excluded(file: Path): Unit =
    log.debug(s"file excluded: $file")

  override def parsedConfig(config: Path, scalafmtVersion: String): Unit =
    log.debug(s"parsed config (v$scalafmtVersion): $config")

  override def downloadWriter(): PrintWriter = new PrintWriter(out)
  override def downloadOutputStreamWriter(): OutputStreamWriter = out

  private class FailedToFormat(filename: String, cause: Throwable)
      extends Exception(filename, cause)
      with NoStackTrace

  private class ScalafmtSbtError(file: Path, cause: Throwable)
      extends Exception(s"sbt-scalafmt failed on $file", cause)
}
