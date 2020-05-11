import java.io.File
import scala.util.{Failure, Success, Try}

fork in ThisBuild := true

lazy val p123 = project
  .in(file("."))
  .aggregate(
    p1,
    p2,
    p3
  )

lazy val p1 = project.settings(
  scalaVersion := "2.10.5"
)
lazy val p2 = project.settings(
  scalaVersion := "2.11.8"
)
lazy val p3 = project.settings(
  scalaVersion := "2.12.1"
)
lazy val p4 = project.settings(
  scalaVersion := "2.12.1"
)
lazy val p5 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtOnCompile := true
)
lazy val p6 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtConfig := file(".scalafmt6.conf")
)
lazy val p7 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtConfig := file(".scalafmt_does_not_exist.conf")
)
lazy val p8 = project.settings(
  scalaVersion := "2.12.1"
)
lazy val p9 = project.settings(
  scalaVersion := "2.12.1"
)
lazy val p10 = project.settings(
  scalaVersion := "2.12.1"
)
lazy val p11 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtConfig := file(".scalafmt11.conf")
)
lazy val p12 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtConfig := file(".scalafmt12.conf")
)
lazy val p13 = project.settings(
  scalaVersion := "2.12.1",
)
lazy val p14 = project.settings(
  scalaVersion := "2.12.1",
)
lazy val p15 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtConfig := file(".scalafmt15.conf")
)
lazy val p16 = project.settings(
  scalaVersion := "2.12.1",
  scalafmtConfig := file(".scalafmt16.conf")
)
lazy val p17 = project.settings(
  scalaVersion := "2.12.1",
  TaskKey[Unit](
    label = "failIffScalafmtCheckFailsBecauseProcessingInaccessibleSource",
    description = "fails if and only if the wrapped scalafmtCheck fails with a FileNotFoundException "
  ) := {
    (Compile / scalafmtCheck).result.value match {
      case Inc(inc: Incomplete) =>
        inc.directCause.collect {
          case e: java.io.FileNotFoundException => throw e
        }
      case _ =>
    }
  }
)

def assertContentsEqual(file: File, expected: String): Unit = {
  val obtained =
    scala.io.Source.fromFile(file).getLines().mkString("\n")

  if (obtained.trim != expected.trim) {
    val msg =
      s"""File: $file
         |Obtained output:
         |$obtained
         |Expected:
         |$expected
         |""".stripMargin
    System.err.println(msg)
    throw new Exception(msg)
  }
}

TaskKey[Unit]("changeTest2") := {
  IO.write(file(s"p8/src/main/scala/Test2.scala"),
    """
      |object
      |Test2
      |{
      |  def foo2(a: Int, // comment
      |    b: Double) = ???
      |}
      |
    """.stripMargin
  )
}

TaskKey[Unit]("check") := {
  (1 to 4).foreach { i =>
    val expectedTest =
      """
        |object Test {
        |  foo(
        |    a, // comment
        |    b
        |  )
        |}
        """.stripMargin
    val expectedMainTest = expectedTest.replaceFirst("Test", "MainTest")
    assertContentsEqual(
      file(s"p$i/src/main/scala/Test.scala"),
      expectedTest
    )
    assertContentsEqual(
      file(s"p$i/src/test/scala/MainTest.scala"),
      expectedMainTest
    )
  }


  assertContentsEqual(
    file(s"p5/src/main/scala/Test.scala"),
    """
      |object Test {
      |  def foo(
      |    a: Int, // comment
      |    b: Double
      |  ) = ???
      |}
    """.stripMargin
  )
  assertContentsEqual(
    file(s"p5/src/test/scala/MainTest.scala"),
    """
      |object MainTest {
      |  def foo(
      |    a: Int, // comment
      |    b: Double
      |  ) = ???
      |}
    """.stripMargin
  )


  assertContentsEqual(
    file(s"p6/src/main/scala/Test.scala"),
    """
      |object Test {
      |  foo(
      |    a, // comment
      |    b
      |  )
      |}
    """.stripMargin
  )
  assertContentsEqual(
    file(s"p6/src/test/scala/MainTest.scala"),
    """
      |object
      |MainTest
      |{
      |  foo(a, // comment
      |    b)
      |}
      |
    """.stripMargin
  )


  assertContentsEqual(
    file(s"p7/src/main/scala/Test.scala"),
    """
      |object
      |Test
      |{
      |  foo(a, // comment
      |    b)
      |}
    """.stripMargin
  )
  assertContentsEqual(
    file(s"p7/src/test/scala/MainTest.scala"),
    """
      |object
      |MainTest
      |{
      |  foo(a, // comment
      |    b)
      |}
    """.stripMargin
  )

  assertContentsEqual(
    file(s"p8/src/main/scala/Test.scala"),
    """
      |object Test {
      |  def foo(
      |    a: Int, // comment
      |    b: Double
      |  ) = ???
      |}
    """.stripMargin
  )

  assertContentsEqual(
    file(s"p8/src/main/scala/Test2.scala"),
    """
      |object Test2 {
      |  def foo2(
      |    a: Int, // comment
      |    b: Double
      |  ) = ???
      |}
    """.stripMargin
  )

  assertContentsEqual(
    file(s"p9/src/main/scala/Test.scala"),
    """
      |object Test {
      |  foo(
      |    a, // comment
      |    b
      |  )
      |}
    """.stripMargin
  )
  assertContentsEqual(
    file(s"p9/src/test/scala/MainTest.scala"),
    """
      |object MainTest {
      |  foo(
      |    a, // comment
      |    b
      |  )
      |}
    """.stripMargin
  )

  assertContentsEqual(
    file("project/plugins.sbt"),
    """
      |addSbtPlugin(
      |  "org.scalameta" % "sbt-scalafmt" % System.getProperty("plugin.version")
      |)
      |resolvers += Resolver.sonatypeRepo("releases")
    """.stripMargin
  )
}


TaskKey[Unit]("checkManagedSources") := {
  assertContentsEqual(
    file("project/x/Something.scala"),
    """
      |// format me
      |object kek {}
      |""".stripMargin
  )

  assertContentsEqual(
    file("project/target/managed.scala"),
    """
      |// don't touch me!!!
      |
      |object a       {}
      |""".stripMargin
  )
}
