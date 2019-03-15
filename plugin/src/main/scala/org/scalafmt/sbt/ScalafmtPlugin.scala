package org.scalafmt.sbt

import java.io.PrintWriter
import java.nio.file.Path

import sbt.Keys._
import sbt.Def
import sbt._
import complete.DefaultParsers._
import sbt.util.CacheStoreFactory
import sbt.util.FileInfo
import sbt.util.FilesInfo
import sbt.util.Logger
import sbt.LoggerWriter

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.scalafmt.interfaces.Scalafmt

object ScalafmtPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val scalafmt = taskKey[Unit]("Format Scala sources with scalafmt.")

    @deprecated("Use scalafmt instead.", "2.0.0")
    val scalafmtIncremental = taskKey[Unit](
      "Format Scala sources to be compiled incrementally with scalafmt (alias to scalafmt)."
    )
    val scalafmtCheck =
      taskKey[Boolean](
        "Fails if a Scala source is mis-formatted. Does not write to files."
      )
    val scalafmtOnCompile =
      settingKey[Boolean](
        "Format Scala source files on compile, off by default."
      )
    val scalafmtConfig = taskKey[File](
      "Location of .scalafmt.conf file. " +
        "If the file does not exist, exception is thrown."
    )
    val scalafmtSbt = taskKey[Unit](
      "Format *.sbt and project/*.scala files for this sbt build."
    )
    val scalafmtSbtCheck =
      taskKey[Boolean](
        "Fails if a *.sbt or project/*.scala source is mis-formatted. " +
          "Does not write to files."
      )
    val scalafmtOnly = inputKey[Unit]("Format a single given file.")
    val scalafmtAll = taskKey[Unit](
      "Execute the scalafmt task for all configurations in which it is enabled. " +
        "(By default this means the Compile and Test configurations.)"
    )
    val scalafmtCheckAll = taskKey[Unit](
      "Execute the scalafmtCheck task for all configurations in which it is enabled. " +
        "(By default this means the Compile and Test configurations.)"
    )
  }
  import autoImport._

  private val scalafmtDoFormatOnCompile =
    taskKey[Unit]("Format Scala source files if scalafmtOnCompile is on.")

  private val scalaConfig = {
    scalafmtConfig.map { f =>
      if (f.exists()) {
        f.toPath
      } else {
        throw new MessageOnlyException(s"File not exists: ${f.toPath}")
      }
    }
  }
  private val sbtConfig = scalaConfig

  private type Input = String
  private type Output = String

  val globalInstance = Scalafmt.create(this.getClass.getClassLoader)

  private def withFormattedSources[T](
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  )(
      onError: (File, Throwable) => T,
      onFormat: (File, Input, Output) => T
  ): Seq[Option[T]] = {
    val reporter = new ScalafmtSbtReporter(log, writer)
    val scalafmtInstance = globalInstance.withReporter(reporter)
    sources
      .map { file =>
        val input = IO.read(file)
        val output =
          scalafmtInstance.format(
            config.toAbsolutePath,
            file.toPath.toAbsolutePath,
            input
          )
        Some(onFormat(file, input, output))
      }
  }

  private def formatSources(
      cacheDirectory: File,
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    cached(cacheDirectory, FilesInfo.lastModified) { modified =>
      val changed = modified.filter(_.exists)
      if (changed.size > 0) {
        log.info(s"Formatting ${changed.size} Scala sources...")
        formatSources(changed.toSeq, config, log, writer)
      }
    }(sources.toSet).getOrElse(())
  }

  private def formatSources(
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    val cnt = withFormattedSources(sources, config, log, writer)(
      (file, e) => {
        log.err(e.toString)
        0
      },
      (file, input, output) => {
        if (input != output) {
          IO.write(file, output)
          1
        } else {
          0
        }
      }
    ).flatten.sum

    if (cnt > 1) {
      log.info(s"Reformatted $cnt Scala sources")
    }

  }

  private def checkSources(
      cacheDirectory: File,
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Boolean = {
    cached[Boolean](cacheDirectory, FilesInfo.lastModified) { modified =>
      val changed = modified.filter(_.exists)
      if (changed.size > 0) {
        log.info(s"Checking ${changed.size} Scala sources...")
        checkSources(changed.toSeq, config, log, writer)
      } else {
        true
      }
    }(sources.toSet).getOrElse(true)
  }

  private def checkSources(
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Boolean = {
    val unformattedCount = withFormattedSources(sources, config, log, writer)(
      (file, e) => {
        log.err(e.toString)
        false
      },
      (file, input, output) => {
        val diff = input != output
        if (diff) {
          log.warn(s"${file.toString} isn't formatted properly!")
        }
        !diff
      }
    ).flatten.count(x => !x)
    if (unformattedCount > 0) {
      throw new MessageOnlyException(
        s"${unformattedCount} files must be formatted"
      )
    }
    unformattedCount == 0
  }

  private def cached[T](cacheBaseDirectory: File, inStyle: FileInfo.Style)(
      action: Set[File] => T
  ): Set[File] => Option[T] = {
    import Path._
    lazy val inCache = Difference.inputs(
      CacheStoreFactory(cacheBaseDirectory).make("in-cache"),
      inStyle
    )
    inputs => {
      inCache(inputs) { inReport =>
        if (!inReport.modified.isEmpty) Some(action(inReport.modified))
        else None
      }
    }
  }

  private lazy val sbtSources = thisProject.map { proj =>
    val rootSbt =
      BuildPaths.configurationSources(proj.base).filterNot(_.isHidden)
    val projectSbt =
      (BuildPaths.projectStandard(proj.base) * GlobFilter("*.sbt")).get
        .filterNot(_.isHidden)
    rootSbt ++ projectSbt
  }
  private lazy val projectSources = thisProject.map { proj =>
    (BuildPaths.projectStandard(proj.base) * GlobFilter("*.scala")).get
  }

  lazy val scalafmtConfigSettings: Seq[Def.Setting[_]] = Seq(
    scalafmt := formatSources(
      streams.value.cacheDirectory,
      (unmanagedSources in scalafmt).value,
      scalaConfig.value,
      streams.value.log,
      streams.value.text()
    ),
    scalafmtIncremental := scalafmt.value,
    scalafmtSbt := {
      formatSources(
        sbtSources.value,
        sbtConfig.value,
        streams.value.log,
        streams.value.text()
      )
      formatSources(
        projectSources.value,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
    },
    scalafmtCheck :=
      checkSources(
        streams.value.cacheDirectory,
        (unmanagedSources in scalafmt).value,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      ),
    scalafmtSbtCheck := {
      checkSources(
        sbtSources.value,
        sbtConfig.value,
        streams.value.log,
        streams.value.text()
      )
      checkSources(
        projectSources.value,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
    },
    scalafmtDoFormatOnCompile := Def.settingDyn {
      if (scalafmtOnCompile.value) {
        scalafmt in resolvedScoped.value.scope
      } else {
        Def.task(())
      }
    }.value,
    compileInputs in compile := (compileInputs in compile)
      .dependsOn(scalafmtDoFormatOnCompile)
      .value,
    scalafmtOnly := {
      val files = spaceDelimited("<files>").parsed
      val absFiles = files.flatMap(fileS => {
        Try { IO.resolve(baseDirectory.value, new File(fileS)) } match {
          case Failure(e) =>
            streams.value.log.error(s"Error with $fileS file: $e")
            None
          case Success(file) => Some(file)
        }
      })

      // scalaConfig
      formatSources(
        absFiles,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
    }
  )

  private val anyConfigsInThisProject = ScopeFilter(
    configurations = inAnyConfiguration
  )

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(inConfig(_)(scalafmtConfigSettings)) ++ Seq(
      scalafmtAll := scalafmt.?.all(anyConfigsInThisProject).value,
      scalafmtCheckAll := scalafmtCheck.?.all(anyConfigsInThisProject).value
    )

  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    scalafmtConfig := {
      (baseDirectory in ThisBuild).value / ".scalafmt.conf"
    }
  )

  override def globalSettings: Seq[Def.Setting[_]] =
    Seq(
      scalafmtOnCompile := false
    )
}
