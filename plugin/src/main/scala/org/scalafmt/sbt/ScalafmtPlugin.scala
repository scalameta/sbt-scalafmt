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

  private def withMaybePlural(count: Int, singular: String) =
    s"$count $singular" + (if (count > 1) "s" else "")

  private def withFormattedSources[T](
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  )(
      onFormat: (File, Input, Output) => T
  ): Set[T] = {
    val reporter = new ScalafmtSbtReporter(log, writer)
    val scalafmtInstance = globalInstance.withReporter(reporter)
    sources.toSet
      .map { file: File =>
        val input = IO.read(file)
        val output =
          scalafmtInstance.format(
            config.toAbsolutePath,
            file.toPath.toAbsolutePath,
            input
          )
        onFormat(file, input, output)
      }
  }

  private def formatSources(
      cacheDirectory: File,
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    cached(cacheDirectory, FilesInfo.lastModified, config) { cacheMisses =>
      val changed = cacheMisses.filter(_.exists)
      if (changed.size > 0) {
        log.info(
          s"Formatting ${withMaybePlural(changed.size, "Scala source")}..."
        )
        formatSources(changed.toSeq, config, log, writer)
      }
      Set.empty
    }(sources.toSet)
  }

  private def formatSources(
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    val cnt = withFormattedSources(sources, config, log, writer)(
      (file, input, output) => {
        if (input != output) {
          IO.write(file, output)
          1
        } else {
          0
        }
      }
    ).sum

    if (cnt > 0) {
      log.info(s"Reformatted ${withMaybePlural(cnt, "Scala source")}")
    }
  }

  private def checkSources(
      cacheDirectory: File,
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    cached(cacheDirectory, FilesInfo.lastModified, config) { cacheMisses =>
      val changed = cacheMisses.filter(_.exists)
      if (changed.size > 0) {
        log.info(
          s"Checking ${withMaybePlural(changed.size, "Scala source")}..."
        )
        checkSources(changed.toSeq, config, log, writer)
      }
    }(sources.toSet)
  }

  private def checkSources(
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    val unformattedFiles = withFormattedSources(sources, config, log, writer)(
      (file, input, output) => {
        val diff = input != output
        if (diff) {
          log.warn(s"${file.toString} isn't formatted properly!")
          Some(file)
        } else {
          None
        }
      }
    ).flatten

    if (!unformattedFiles.isEmpty) {
      throw new MessageOnlyException(
        s"${withMaybePlural(unformattedFiles.size, "file")} must be formatted"
      )
    }
  }

  private def cached(
      cacheBaseDirectory: File,
      outStyle: FileInfo.Style,
      config: Path
  )(
      action: Set[File] => Unit
  ): Set[File] => Unit = {
    lazy val outCache = Difference.outputs(
      CacheStoreFactory(cacheBaseDirectory).make("out-cache"),
      outStyle
    )
    inputs => {
      outCache(inputs + config.toFile) { outReport =>
        val updatedOrAdded = outReport.modified -- outReport.removed
        if (!updatedOrAdded.isEmpty) {
          val cacheMisses = updatedOrAdded.intersect(inputs)
          if (!cacheMisses.isEmpty) {
            // incremental run
            action(cacheMisses)
          } else {
            // config file must have changed, rerun everything
            action(inputs)
          }
        }
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
    scalafmtCheck := {
      checkSources(
        (streams in scalafmt).value.cacheDirectory,
        (unmanagedSources in scalafmt).value,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
      true // the return is useless as an exception is thrown on error
    },
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
      true // the return is useless as an exception is thrown on error
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
