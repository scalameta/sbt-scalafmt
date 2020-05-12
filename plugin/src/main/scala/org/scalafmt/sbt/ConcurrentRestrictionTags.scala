package org.scalafmt.sbt

trait ConcurrentRestrictionTags {
  import sbt.ConcurrentRestrictions.Tag

  /**
    * This tag can be used to control the maximum number of parallel scalafmt tasks in large-scale build trees.
    *
    * Global / concurrentRestrictions ++= Tags.limit(org.scalafmt.sbt.ConcurrentRestrictionTags.Scalafmt, 3)
    *
    * would prevent SBT from spawning more than three simultaneous Scalafmt tasks
    *
    * @see https://www.scala-sbt.org/1.x/docs/Parallel-Execution.html
    */
  val Scalafmt = Tag("scalafmt")
}

object ConcurrentRestrictionTags extends ConcurrentRestrictionTags
