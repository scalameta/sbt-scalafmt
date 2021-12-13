package org.scalafmt.sbt

private[sbt] class ErrorHandling(
    val printDiff: Boolean,
    val logOnEachError: Boolean,
    val failOnErrors: Boolean,
    val detailedErrorEnabled: Boolean
)
