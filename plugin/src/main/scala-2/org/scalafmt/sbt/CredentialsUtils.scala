package org.scalafmt.sbt

import sbt.librarymanagement.ivy.{Credentials, DirectCredentials}

object CredentialsUtils {
  def toDirect(c: Credentials): DirectCredentials = Credentials.toDirect(c)
}
