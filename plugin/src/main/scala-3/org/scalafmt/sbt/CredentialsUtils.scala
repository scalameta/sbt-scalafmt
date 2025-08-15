package org.scalafmt.sbt

import sbt.internal.librarymanagement.ivy.IvyCredentials
import sbt.librarymanagement.Credentials

object CredentialsUtils {
  def toDirect(c: Credentials): Credentials.DirectCredentials = IvyCredentials
    .toDirect(c)
}
