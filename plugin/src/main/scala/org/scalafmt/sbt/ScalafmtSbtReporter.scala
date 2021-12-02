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
    detailedErrorEnabled: Boolean
) extends ScalafmtReporter {
  import ScalafmtSbtReporter._

  override def error(file: Path, message: String): Unit =
    error(file, message, null)

  override def error(file: Path, e: Throwable): Unit =
    error(file, null, e)

  override def error(file: Path, message: String, e: Throwable): Unit = {
    def getMessage() = {
      val res = new StringWriter()
      res.write("scalafmt: ")
      res.write(Option(message).getOrElse("failed"))
      res.write(" [")
      res.write(file.toString)
      res.write(']')
      if (null != e) {
        if (!detailedErrorEnabled)
          Option(e.getMessage).foreach { x =>
            res.write(" ")
            res.write(x)
          }
      }
      res.toString
    }

    val cause = if (detailedErrorEnabled) e else null
    throw new ScalafmtSbtError(getMessage(), cause)
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
      extends RuntimeException(message, cause)

}
