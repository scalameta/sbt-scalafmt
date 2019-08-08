package org.scalafmt.sbt

import java.io.File

import sbt.io.IO
import sbt.util.{CacheStore, ChangeReport, FileInfo, FilesInfo, Tracked}

// Originally copied from https://github.com/sbt/util/blob/7431dbd/util-tracking/src/main/scala/sbt/util/Tracked.scala
class Difference(
    val store: CacheStore,
    val style: FileInfo.Style,
    val defineClean: Boolean,
    val filesAreOutputs: Boolean
) extends Tracked {
  def clean() = {
    if (defineClean) IO.delete(raw(cachedFilesInfo)) else ()
    clearCache()
  }

  private def clearCache() = store.delete()

  private def cachedFilesInfo =
    store.read(default = FilesInfo.empty[style.F])(style.formats).files
  private def raw(fs: Set[style.F]): Set[File] = fs.map(_.file)

  private def always[B](result: B): PartialFunction[Any, B] = {
    case _ => result
  }

  def apply[T](files: Set[File])(
      f: ChangeReport[File] => T,
      toCache: PartialFunction[T, Set[File]] = always(files)
  ): T = {
    val lastFilesInfo = cachedFilesInfo
    apply(files, lastFilesInfo)(f)(toCache)
  }

  def apply[T](
      f: ChangeReport[File] => T
  )(implicit @deprecatedName('toFiles) toCache: T => Set[File]): T = {
    val lastFilesInfo = cachedFilesInfo
    apply(raw(lastFilesInfo), lastFilesInfo)(f) { case x => toCache(x) }
  }

  private def abs(files: Set[File]) = files.map(_.getAbsoluteFile)

  private[this] def apply[T](files: Set[File], lastFilesInfo: Set[style.F])(
      f: ChangeReport[File] => T
  )(toCache: PartialFunction[T, Set[File]]): T = {
    val lastFiles = raw(lastFilesInfo)
    val currentFiles = abs(files)
    val currentFilesInfo = style(currentFiles)

    val report = new ChangeReport[File] {
      lazy val checked = currentFiles
      lazy val removed = lastFiles -- checked // all files that were included previously but not this time.  This is independent of whether the files exist.
      lazy val added = checked -- lastFiles // all files included now but not previously.  This is independent of whether the files exist.
      lazy val modified = raw(lastFilesInfo -- currentFilesInfo.files) ++ added
      lazy val unmodified = checked -- modified
    }

    val result = f(report)
    val maybeInfo =
      if (filesAreOutputs) toCache.lift(result).map(f => style(abs(f)))
      else Some(currentFilesInfo)

    maybeInfo.foreach(info => store.write(info)(style.formats))

    result
  }
}
