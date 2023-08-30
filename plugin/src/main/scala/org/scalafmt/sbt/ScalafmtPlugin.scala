package org.scalafmt.sbt

import java.io.OutputStreamWriter
import java.nio.file.Path

import sbt.Keys._
import sbt._
import sbt.librarymanagement.MavenRepository
import sbt.util.CacheImplicits._
import sbt.util.CacheStoreFactory
import sbt.util.FileInfo
import sbt.util.Level

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalafmt.interfaces.RepositoryCredential
import org.scalafmt.interfaces.Scalafmt
import org.scalafmt.sysops.AbsoluteFile
import org.scalafmt.sysops.FileOps
import org.scalafmt.sysops.GitOps

import complete.DefaultParsers._

object ScalafmtPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val scalafmt = taskKey[Unit]("Format Scala sources with scalafmt.")

    private[sbt] val ScalafmtTagPack =
      Seq(ConcurrentRestrictionTags.Scalafmt, Tags.CPU)

    @deprecated("Use scalafmt instead.", "2.0.0")
    val scalafmtIncremental = taskKey[Unit](
      "Format Scala sources to be compiled incrementally with scalafmt (alias to scalafmt)."
    )
    val scalafmtCheck =
      taskKey[Unit](
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
      taskKey[Unit](
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
    val scalafmtDetailedError =
      settingKey[Boolean](
        "Enables logging of detailed errors with stacktraces, disabled by default"
      )
    val scalafmtFilter = settingKey[String](
      "File filtering mode when running scalafmt."
    )
    val scalafmtLogOnEachError = settingKey[Boolean](
      "Enables logging on an error."
    )
    val scalafmtFailOnErrors = settingKey[Boolean](
      "Controls whether to fail in case of formatting errors."
    )
    val scalafmtPrintDiff = settingKey[Boolean](
      "Enables full diff output when running check."
    )
  }

  import autoImport._

  case class ScalafmtAnalysis(failedScalafmtCheck: Set[File])
  object ScalafmtAnalysis {
    import sjsonnew.{:*:, LList, LNil}
    implicit val analysisIso = LList.iso(
      { a: ScalafmtAnalysis =>
        ("failedScalafmtCheck", a.failedScalafmtCheck) :*: LNil
      },
      { in: Set[File] :*: LNil =>
        ScalafmtAnalysis(in.head)
      }
    )
  }

  private val scalafmtDoFormatOnCompile =
    taskKey[Unit]("Format Scala source files if scalafmtOnCompile is on.")

  private val scalaConfig =
    scalafmtConfig.map { f =>
      if (f.exists()) {
        f.toPath
      } else {
        throw messageException(s"File does not exist: ${f.toPath}")
      }
    }
  private val sbtConfig = scalaConfig

  private type Input = String
  private type Output = String
  private type InitTask = Def.Initialize[Task[Unit]]

  val globalInstance = Scalafmt
    .create(this.getClass.getClassLoader)
    .withRespectProjectFilters(true)

  private object FilterMode {
    val diffDirty = "diff-dirty"
    val diffRefPrefix = "diff-ref="
    val none = "none"
  }

  private def getLogMessage(message: String): String = "scalafmt: " + message

  private def messageException(message: String): RuntimeException =
    new MessageOnlyException(getLogMessage(message))

  private class ScalafmtLogger(log: Logger) extends Logger {
    override def trace(t: => Throwable): Unit = log.trace(t)
    override def success(message: => String): Unit = success(message)
    override def log(level: Level.Value, message: => String): Unit =
      log.log(level, getLogMessage(message))
  }

  private class FormatSession(
      config: Path,
      taskStreams: TaskStreams,
      cacheStoreFactory: CacheStoreFactory,
      resolvers: Seq[Resolver],
      credentials: Seq[Credentials],
      currentProject: ResolvedProject,
      filterMode: String,
      errorHandling: ErrorHandling
  ) {
    private val log = new ScalafmtLogger(taskStreams.log)
    private val reporter = new ScalafmtSbtReporter(
      log,
      new OutputStreamWriter(taskStreams.binary()),
      errorHandling
    )

    private val scalafmtSession = {
      val repositories = resolvers.collect { case r: MavenRepository =>
        r.root
      }
      val repoCredentials = credentials.flatMap { c =>
        Try(Credentials.toDirect(c)).toOption.map { dc =>
          new RepositoryCredential(dc.host, dc.userName, dc.passwd)
        }
      }

      log.debug(
        repositories.mkString("Adding repositories [", ",", "]")
      )
      log.debug {
        val info = repoCredentials.map(x => s"${x.username}@${x.host}")
        info.mkString("Adding credentials [", ",", "]")
      }

      val scalafmtSession =
        globalInstance
          .withReporter(reporter)
          .withMavenRepositories(repositories: _*)
          .withRepositoryCredentials(repoCredentials: _*)
          .createSession(config.toAbsolutePath)
      if (scalafmtSession == null)
        throw messageException(
          "failed to create formatting session. Please report bug to https://github.com/scalameta/sbt-scalafmt"
        )
      scalafmtSession
    }

    private lazy val baseDir: Path = currentProject.base.getCanonicalFile.toPath

    @inline private def asRelative(file: File): String =
      baseDir.relativize(file.getCanonicalFile.toPath).toString

    private def filterFiles(sources: Seq[File], dirs: Seq[File]): Seq[File] = {
      val filter = getFileFilter(dirs)
      sources.map(_.getCanonicalFile).distinct.filter { file =>
        val path = file.toPath
        scalafmtSession.matchesProjectFilters(path) && filter(path)
      }
    }

    private def getFileFilter(dirs: Seq[File]): Path => Boolean = {
      // dirs don't have to be within baseDir but within the same git tree
      def absDirs = dirs.map(x => AbsoluteFile(x.getCanonicalFile.toPath))
      def gitOps = GitOps.FactoryImpl(AbsoluteFile(baseDir))
      def getFromFiles(getFiles: => Seq[AbsoluteFile], gitCmd: => String) = {
        def gitMessage = s"[git $gitCmd] ($baseDir)"
        Try(getFiles) match {
          case Failure(x) =>
            log.warn(s"format all files; $gitMessage: ${x.getMessage}")
            _: Path => true
          case Success(x) =>
            log.debug(s"considering ${x.length} files $gitMessage")
            FileOps.getFileMatcher(x.map(_.path))
        }
      }

      if (filterMode == FilterMode.diffDirty)
        getFromFiles(gitOps.status(absDirs: _*), "status")
      else if (filterMode.startsWith(FilterMode.diffRefPrefix)) {
        val branch = filterMode.substring(FilterMode.diffRefPrefix.length)
        getFromFiles(gitOps.diff(branch, absDirs: _*), s"diff $branch")
      } else if (filterMode != FilterMode.none && scalafmtSession.isGitOnly)
        getFromFiles(gitOps.lsTree(absDirs: _*), "ls-files")
      else {
        log.debug("considering all files (no git)")
        _ => true
      }
    }

    private def withFormattedSources[T](initial: T, sources: Seq[File])(
        onFormat: (T, File, Input, Output) => T
    ): T = {
      var res = initial
      var good = 0
      var bad = 0
      sources.foreach { file =>
        val path = file.toPath.toAbsolutePath
        Try(IO.read(file)) match {
          case Failure(x) =>
            reporter.error(path, "Failed to read", x)
            bad += 1
          case Success(x) =>
            val output = scalafmtSession.formatOrError(path, x)
            /* no need to report on exception since for all errors
             * reporter.error would have been called already */
            Option(output.value) match {
              case None => bad += 1
              case Some(o) =>
                if (x == o) good += 1
                else res = onFormat(res, file, x, o)
            }
        }
      }
      if (bad != 0) {
        val err = s"failed for $bad sources"
        if (!errorHandling.failOnErrors) log.error(err)
        else throw messageException(err)
      }
      log.debug(s"Unchanged $good Scala sources")
      res
    }

    def formatTrackedSources(sources: Seq[File], dirs: Seq[File]): Unit = {
      val filteredSources = filterFiles(sources, dirs)
      trackSourcesAndConfig(cacheStoreFactory, filteredSources) {
        (outDiff, configChanged, prev) =>
          val filesToFormat: Seq[File] =
            if (configChanged) filteredSources
            else {
              // in addition to the detected changes, process files that failed scalafmtCheck
              // we can ignore the succeeded files because, they don't require reformatting
              val updatedOrAdded = outDiff.modified & outDiff.checked
              (updatedOrAdded | prev.failedScalafmtCheck).toSeq
            }
          formatFilteredSources(filesToFormat)
          ScalafmtAnalysis(Set.empty)
      }
    }

    def formatSources(sources: Seq[File], dirs: Seq[File]): Unit =
      formatFilteredSources(filterFiles(sources, dirs))

    private def formatFilteredSources(sources: Seq[File]): Unit = {
      if (sources.nonEmpty)
        log.info(s"Formatting ${sources.length} Scala sources ($baseDir)...")
      val cnt = withFormattedSources(0, sources) { (res, file, _, output) =>
        IO.write(file, output)
        res + 1
      }
      if (cnt > 0) log.info(s"Reformatted $cnt Scala sources")
    }

    def checkTrackedSources(sources: Seq[File], dirs: Seq[File]): Unit = {
      val filteredSources = filterFiles(sources, dirs)
      val result = trackSourcesAndConfig(cacheStoreFactory, filteredSources) {
        (outDiff, configChanged, prev) =>
          val filesToCheck: Seq[File] =
            if (configChanged) filteredSources
            else (outDiff.modified & outDiff.checked).toSeq
          val prevFailed: Set[File] =
            if (configChanged) Set.empty
            else prev.failedScalafmtCheck & outDiff.unmodified
          if (prevFailed.nonEmpty) {
            val files: Seq[String] =
              prevFailed.map(asRelative)(collection.breakOut)
            val prefix =
              s"$baseDir: ${files.length} files aren't formatted properly:\n"
            log.warn(files.sorted.mkString(prefix, "\n", ""))
          }
          val result =
            checkFilteredSources(filesToCheck)
          prev.copy(
            failedScalafmtCheck = result.failedScalafmtCheck | prevFailed
          )
      }
      throwOnFailure(result)
    }

    def checkSources(sources: Seq[File], dirs: Seq[File]): Unit =
      throwOnFailure(checkFilteredSources(filterFiles(sources, dirs)))

    private def checkFilteredSources(sources: Seq[File]): ScalafmtAnalysis = {
      if (sources.nonEmpty) {
        log.info(s"Checking ${sources.size} Scala sources ($baseDir)...")
      }
      val unformatted = Set.newBuilder[File]
      withFormattedSources(Unit, sources) { (_, file, input, output) =>
        val diff = if (errorHandling.printDiff) {
          DiffUtils.unifiedDiff("/" + asRelative(file), input, output)
        } else ""
        val suffix = if (diff.isEmpty) "" else '\n' + diff
        log.warn(s"$file isn't formatted properly!$suffix")
        unformatted += file
        Unit
      }
      ScalafmtAnalysis(failedScalafmtCheck = unformatted.result())
    }

    // This tracks
    // 1. previous value
    // 2. changes to the config file
    // 3. changes to source and their last modified dates after the operation
    // The tracking is shared between scalafmt and scalafmtCheck
    private def trackSourcesAndConfig(
        cacheStoreFactory: CacheStoreFactory,
        sources: Seq[File]
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
        ) { case (configChanged, configHash) =>
          Tracked.diffOutputs(
            cacheStoreFactory.make("output-diff"),
            FileInfo.lastModified
          )(sources.toSet) { (outDiff: ChangeReport[File]) =>
            log.debug(outDiff.toString())
            f(outDiff, configChanged, prev)
          }
        }
        tracker(FileInfo.hash(config.toFile))
      }
      prevTracker(())
    }

    private def throwOnFailure(analysis: ScalafmtAnalysis): Unit = {
      val failed = analysis.failedScalafmtCheck
      if (failed.nonEmpty)
        throw messageException(
          s"${failed.size} files must be formatted ($baseDir)"
        )
    }
  }

  private lazy val sbtSources = Def.task {
    val rootBase = (LocalRootProject / baseDirectory).value
    val thisBase = thisProject.value.base
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
    val thisBase = thisProject.value.base

    if (rootBase == thisBase) {
      val projectDirectory = BuildPaths.projectStandard(thisBase)
      val targetDirectory =
        BuildPaths.outputDirectory(projectDirectory).getAbsolutePath
      projectDirectory
        .descendantsExcept(
          "*.scala",
          (pathname: File) =>
            pathname.getAbsolutePath.startsWith(targetDirectory)
        )
        .get
    } else {
      Nil
    }
  }

  private def scalafmtTask(
      sources: Seq[File],
      dirs: Seq[File],
      session: FormatSession
  ) =
    Def.task {
      session.formatTrackedSources(sources, dirs)
    } tag (ScalafmtTagPack: _*)

  private def scalafmtCheckTask(
      sources: Seq[File],
      dirs: Seq[File],
      session: FormatSession
  ) =
    Def.task {
      session.checkTrackedSources(sources, dirs)
    } tag (ScalafmtTagPack: _*)

  private def getScalafmtSourcesTask(
      f: (Seq[File], Seq[File], FormatSession) => InitTask
  ) = Def.taskDyn[Unit] {
    val sources = (unmanagedSources in scalafmt).?.value.getOrElse(Seq.empty)
    val dirs = (unmanagedSourceDirectories in scalafmt).?.value.getOrElse(Nil)
    getScalafmtTask(f)(sources, dirs, scalaConfig.value)
  }

  private def scalafmtSbtTask(
      sources: Seq[File],
      dirs: Seq[File],
      session: FormatSession
  ) = Def.task {
    session.formatSources(sources, dirs)
  } tag (ScalafmtTagPack: _*)

  private def scalafmtSbtCheckTask(
      sources: Seq[File],
      dirs: Seq[File],
      session: FormatSession
  ) = Def.task {
    session.checkSources(sources, dirs)
  } tag (ScalafmtTagPack: _*)

  private def getScalafmtSbtTasks(
      func: (Seq[File], Seq[File], FormatSession) => InitTask
  ) = Def.taskDyn {
    joinScalafmtTasks(func)(
      (sbtSources.value, Nil, sbtConfig.value),
      (metabuildSources.value, Nil, scalaConfig.value)
    )
  }

  private def joinScalafmtTasks(
      func: (Seq[File], Seq[File], FormatSession) => InitTask
  )(tuples: (Seq[File], Seq[File], Path)*) = {
    val tasks = tuples.map { case (files, dirs, config) =>
      getScalafmtTask(func)(files, dirs, config)
    }
    Def.sequential(tasks.tail.toList, tasks.head)
  }

  private def getScalafmtTask(
      func: (Seq[File], Seq[File], FormatSession) => InitTask
  )(files: Seq[File], dirs: Seq[File], config: Path) = Def.taskDyn[Unit] {
    if (files.isEmpty) Def.task(Unit)
    else {
      val session = new FormatSession(
        config,
        streams.value,
        (scalafmt / streams).value.cacheStoreFactory,
        fullResolvers.value,
        credentials.value,
        thisProject.value,
        scalafmtFilter.value,
        new ErrorHandling(
          scalafmtPrintDiff.value,
          scalafmtLogOnEachError.value,
          scalafmtFailOnErrors.value,
          scalafmtDetailedError.value
        )
      )
      func(files, dirs, session)
    }
  }

  lazy val scalafmtConfigSettings: Seq[Def.Setting[_]] = Seq(
    scalafmt := getScalafmtSourcesTask(scalafmtTask).value,
    scalafmtIncremental := scalafmt.value,
    scalafmtSbt := getScalafmtSbtTasks(scalafmtSbtTask).value,
    scalafmtCheck := getScalafmtSourcesTask(scalafmtCheckTask).value,
    scalafmtSbtCheck := getScalafmtSbtTasks(scalafmtSbtCheckTask).value,
    scalafmtDoFormatOnCompile := Def.settingDyn {
      if (scalafmtOnCompile.value) {
        scalafmt in resolvedScoped.value.scope
      } else {
        Def.task(())
      }
    }.value,
    sources in Compile := (sources in Compile)
      .dependsOn(scalafmtDoFormatOnCompile)
      .value,
    scalafmtOnly := {
      val files = spaceDelimited("<files>").parsed
      val absFiles = files.flatMap { fileS =>
        Try(IO.resolve(baseDirectory.value, new File(fileS))) match {
          case Failure(e) =>
            streams.value.log.error(s"Error with $fileS file: $e")
            None
          case Success(file) => Some(file)
        }
      }

      // scalaConfig
      new FormatSession(
        scalaConfig.value,
        streams.value,
        (scalafmt / streams).value.cacheStoreFactory,
        fullResolvers.value,
        credentials.value,
        thisProject.value,
        "",
        new ErrorHandling(
          scalafmtPrintDiff.value,
          scalafmtLogOnEachError.value,
          scalafmtFailOnErrors.value,
          scalafmtDetailedError.value
        )
      ).formatSources(absFiles, Nil)
    }
  )

  private val anyConfigsInThisProject = ScopeFilter(
    configurations = inAnyConfiguration
  )

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test, IntegrationTest).flatMap {
      inConfig(_)(scalafmtConfigSettings)
    } ++ Seq(
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
      scalafmtFilter := "",
      scalafmtOnCompile := false,
      scalafmtLogOnEachError := false,
      scalafmtFailOnErrors := true,
      scalafmtPrintDiff := false,
      scalafmtDetailedError := false
    )

}
