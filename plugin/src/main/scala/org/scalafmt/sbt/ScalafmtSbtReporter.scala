package org.scalafmt.sbt

import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path

import sbt.util.Logger

import org.scalafmt.interfaces.ScalafmtReporter

class ScalafmtSbtReporter(
    log: Logger,
    out: OutputStreamWriter,
    errorHandling: ErrorHandling
) extends ScalafmtReporter {
  import ScalafmtSbtReporter._

  override def error(file: Path, message: String): Unit =
    error(file, message, null)

  override def error(file: Path, e: Throwable): Unit =
    error(file, null, e)

  override def error(file: Path, message: String, e: Throwable): Unit = {
    def getMessage(toThrow: Boolean) = {
      val res = new StringWriter()
      if (toThrow) res.write("scalafmt: ")
      val nestedMessage = if (e == null) None else Option(e.getMessage)
      val messageOpt = Option(message).orElse(nestedMessage)
      res.write(messageOpt.getOrElse("failed"))
      res.write(" [")
      res.write(file.toString)
      res.write(']')
      if (null != e && !toThrow) {
        if (errorHandling.detailedErrorEnabled)
          e.printStackTrace(new PrintWriter(res))
        else if (messageOpt ne nestedMessage) nestedMessage.foreach { x =>
          res.write(": ")
          res.write(x)
        }
      }
      res.toString
    }

    if (errorHandling.logOnEachError) log.error(getMessage(false))
    else if (errorHandling.failOnErrors) {
      val cause = if (errorHandling.detailedErrorEnabled) e else null
      throw new ScalafmtSbtError(getMessage(true), cause)
    }
  }

  override def excluded(file: Path): Unit =
    log.debug(s"file excluded: $file")

  override def parsedConfig(config: Path, scalafmtVersion: String): Unit =
    log.debug(s"parsed config (v$scalafmtVersion): $config")

  override def downloadWriter(): PrintWriter = new PrintWriter(out)
  override def downloadOutputStreamWriter(): OutputStreamWriter = out
}

object ScalafmtSbtReporter {

  private class ScalafmtSbtError(message: String, cause: Throwable)
      extends RuntimeException(message, cause, true, cause != null)

}
