package org.scalafmt.sbt

import java.io.PrintWriter
import java.nio.file.Path

import sbt.Keys._
import sbt.Def
import sbt._
import complete.DefaultParsers._
import sbt.util.CacheImplicits._
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

  case class ScalafmtAnalysis(failedScalafmtCheck: Set[File])
  object ScalafmtAnalysis {
    import sjsonnew.{:*:, LList, LNil}
    implicit val analysisIso = LList.iso({ a: ScalafmtAnalysis =>
      ("failedScalafmtCheck", a.failedScalafmtCheck) :*: LNil
    }, { in: Set[File] :*: LNil =>
      ScalafmtAnalysis(in.head)
    })
  }

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
      cacheStoreFactory: CacheStoreFactory,
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    trackSourcesAndConfig(cacheStoreFactory, sources, config) {
      (outDiff, configChanged, prev) =>
        log.debug(outDiff.toString)
        val updatedOrAdded = outDiff.modified & outDiff.checked
        val filesToFormat: Set[File] =
          if (configChanged) sources.toSet
          else {
            // in addition to the detected changes, process files that failed scalafmtCheck
            // we can ignore the succeeded files because, they don't require reformatting
            updatedOrAdded | prev.failedScalafmtCheck
          }
        if (filesToFormat.nonEmpty) {
          log.info(s"Formatting ${filesToFormat.size} Scala sources...")
          formatSources(filesToFormat, config, log, writer)
        }
        ScalafmtAnalysis(Set.empty)
    }
  }

  private def formatSources(
      sources: Set[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): Unit = {
    val cnt = withFormattedSources(sources.toSeq, config, log, writer)(
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
      cacheStoreFactory: CacheStoreFactory,
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): ScalafmtAnalysis = {
    trackSourcesAndConfig(cacheStoreFactory, sources, config) {
      (outDiff, configChanged, prev) =>
        log.debug(outDiff.toString)
        val updatedOrAdded = outDiff.modified & outDiff.checked
        val filesToCheck: Set[File] =
          if (configChanged) sources.toSet
          else updatedOrAdded
        val prevFailed: Set[File] =
          if (configChanged) Set.empty
          else prev.failedScalafmtCheck & outDiff.unmodified
        prevFailed foreach { warnBadFormat(_, log) }
        val result = checkSources(filesToCheck.toSeq, config, log, writer)
        prev.copy(
          failedScalafmtCheck = result.failedScalafmtCheck | prevFailed
        )
    }
  }

  private def trueOrBoom(analysis: ScalafmtAnalysis): Boolean = {
    val failureCount = analysis.failedScalafmtCheck.size
    if (failureCount > 0) {
      throw new MessageOnlyException(
        s"${failureCount} files must be formatted"
      )
    }
    true
  }

  private def warnBadFormat(file: File, log: Logger): Unit = {
    log.warn(s"${file.toString} isn't formatted properly!")
  }

  private def checkSources(
      sources: Seq[File],
      config: Path,
      log: Logger,
      writer: PrintWriter
  ): ScalafmtAnalysis = {
    log.info(s"Checking ${sources.size} Scala sources...")
    val unformatted = withFormattedSources(sources, config, log, writer)(
      (file, input, output) => {
        val diff = input != output
        if (diff) {
          warnBadFormat(file, log)
          Some(file)
        } else None
      }
    ).flatten.flatten.toSet
    ScalafmtAnalysis(failedScalafmtCheck = unformatted)
  }

  // This tracks
  // 1. previous value
  // 2. changes to the config file
  // 3. changes to source and their last modified dates after the operation
  // The tracking is shared between scalafmt and scalafmtCheck
  private def trackSourcesAndConfig(
      cacheStoreFactory: CacheStoreFactory,
      sources: Seq[File],
      config: Path
  )(
      f: (ChangeReport[File], Boolean, ScalafmtAnalysis) => ScalafmtAnalysis
  ): ScalafmtAnalysis = {
    // use prevTracker to share previous values between tasks
    val prevTracker = Tracked.lastOutput[Unit, ScalafmtAnalysis](
      cacheStoreFactory.make("last")
    ) { (_, prev0) =>
      val prev = prev0.getOrElse(ScalafmtAnalysis(Set.empty))
      val tracker = Tracked.inputChanged[HashFileInfo, ScalafmtAnalysis](
        cacheStoreFactory.make("config")
      ) {
        case (configChanged, configHash) =>
          Tracked.diffOutputs(
            cacheStoreFactory.make("output-diff"),
            FileInfo.lastModified
          )(sources.toSet) { (outDiff: ChangeReport[File]) =>
            f(outDiff, configChanged, prev)
          }
      }
      tracker(FileInfo.hash(config.toFile))
    }
    prevTracker(())
  }

  private lazy val sbtSources = Def.task {
    val rootBase = (LocalRootProject / baseDirectory).value
    val thisBase = (thisProject.value).base
    val rootSbt =
      BuildPaths.configurationSources(thisBase).filterNot(_.isHidden)
    val metabuildSbt =
      if (rootBase == thisBase)
        (BuildPaths.projectStandard(thisBase) ** GlobFilter("*.sbt")).get
      else Nil
    rootSbt ++ metabuildSbt
  }

  private lazy val metabuildSources = Def.task {
    val rootBase = (LocalRootProject / baseDirectory).value
    val thisBase = (thisProject.value).base
    if (rootBase == thisBase)
      (BuildPaths.projectStandard(thisBase) ** GlobFilter("*.scala")).get
    else Nil
  }

  lazy val scalafmtConfigSettings: Seq[Def.Setting[_]] = Seq(
    scalafmt := {
      formatSources(
        streams.value.cacheStoreFactory,
        (unmanagedSources in scalafmt).value,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
    },
    scalafmtIncremental := scalafmt.value,
    scalafmtSbt := {
      formatSources(
        sbtSources.value.toSet,
        sbtConfig.value,
        streams.value.log,
        streams.value.text()
      )
      formatSources(
        metabuildSources.value.toSet,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
    },
    scalafmtCheck := {
      val analysis = checkSources(
        (scalafmt / streams).value.cacheStoreFactory,
        (unmanagedSources in scalafmt).value,
        scalaConfig.value,
        streams.value.log,
        streams.value.text()
      )
      trueOrBoom(analysis)
    },
    scalafmtSbtCheck := {
      trueOrBoom(
        checkSources(
          sbtSources.value,
          sbtConfig.value,
          streams.value.log,
          streams.value.text()
        )
      )
      trueOrBoom(
        checkSources(
          metabuildSources.value,
          scalaConfig.value,
          streams.value.log,
          streams.value.text()
        )
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
        absFiles.toSet,
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
