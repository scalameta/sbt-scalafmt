package org.scalafmt.sbt

import java.io.File
import java.lang as jl

import sbt.Keys.TaskStreams
import sbt.internal.librarymanagement.InternalDefaults.{
  modulePrefixTemp, sbtOrgTemp,
}
import sbt.{librarymanagement as lm, richUpdateReport}

import org.scalafmt.CompatCollections.JavaConverters.*
import org.scalafmt.interfaces.*

class ScalafmtSbtDependencyDownloader(
    taskStreams: TaskStreams,
    csrConfiguration: lmcoursier.CoursierConfiguration,
    updateConfiguration: lm.UpdateConfiguration,
) extends RepositoryPackageDownloaderFactory with RepositoryPackageDownloader {
  // 2.6.0 (537203c) switched from a bundled coursier to sbt's resolver, so
  // the project's csrConfiguration now flows into scalafmt-cli's parent-null
  // URLClassLoader. Keep the infrastructure (resolvers, credentials, cache),
  // strip the policy — those are rules for the user's deps, not ours.
  private val dependencyResolution: lm.DependencyResolution = lmcoursier
    .CoursierDependencyResolution(
      csrConfiguration.withForceVersions(Vector.empty)
        .withExcludeDependencies(Vector.empty).withReconciliation(Vector.empty)
        .withStrict(None).withSameVersions(Seq.empty)
        .withFallbackDependencies(Vector.empty)
        .withInterProjectDependencies(Vector.empty)
        .withExtraProjects(Vector.empty),
    )

  override def create(
      reporter: ScalafmtReporter,
      properties: RepositoryProperties,
  ): RepositoryPackageDownloader = this

  override def download(
      scalaVersion: String,
      scalafmtVersion: String,
      reporter: ScalafmtReporter,
      dependencies: jl.Iterable[RepositoryPackage],
  ): jl.Iterable[File] = {
    val name = s"${modulePrefixTemp}scalafmt-${scalafmtVersion}_$scalaVersion"
    val moduleId = lm.ModuleID(sbtOrgTemp, name, "1.0")
    val scalaModule = lm.ScalaModuleInfo(
      scalaFullVersion = scalaVersion,
      scalaBinaryVersion = lm.CrossVersion.binaryScalaVersion(scalaVersion),
      configurations = Vector.empty,
      checkExplicit = true,
      filterImplicit = false, // must include scala-library in resolved jars;
      // the classloader is fully isolated (parent = null), so all
      // dependencies must be explicitly present (#440)
      overrideScalaVersion = false, // disables autoScalaLibrary
    )
    val moduleDependencies = dependencies.asScala
      .map(d => lm.ModuleID(d.group, d.artifact, d.version)).toVector
    val moduleConf = lm
      .ModuleDescriptorConfiguration(moduleId, lm.ModuleInfo("sandbox"))
      .withDependencies(moduleDependencies).withScalaModuleInfo(scalaModule)

    dependencyResolution.update(
      dependencyResolution.moduleDescriptor(moduleConf),
      updateConfiguration,
      lm.UnresolvedWarningConfiguration(),
      taskStreams.log,
    ).fold(x => throw x.resolveException, x => x.allFiles.asJava)
  }

}
