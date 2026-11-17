package org.scalafmt.sbt

import sbt.librarymanagement.{CredentialUtils, Credentials}

object CredentialsUtils {
  def toDirect(c: Credentials): Credentials.DirectCredentials = CredentialUtils
    .toDirect(c)
}
