package org.scalafmt.sbt

import sbt.librarymanagement.ivy.Credentials
import sbt.librarymanagement.ivy.DirectCredentials

object CredentialsUtils {
  def toDirect(c: Credentials): DirectCredentials = Credentials.toDirect(c)
}
