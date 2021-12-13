package org.scalafmt.sbt

import org.scalafmt.CompatCollections.JavaConverters._

import difflib.DiffUtils._

object DiffUtils {

  def unifiedDiff(file: String, input: String, output: String): String = {
    def jList(code: String, addEol: Boolean) = {
      val last = if (addEol) Iterator.single("") else Iterator.empty
      (code.linesIterator ++ last).toIndexedSeq.asJava
    }
    val a = jList(input, false)
    // output always has EOL; if input doesn't, pretend output has extra line
    val inputNoEol = input.lastOption.forall(x => x != '\n' && x != '\r')
    val b = jList(output, inputNoEol)
    val patch =
      generateUnifiedDiff(s"a$file", s"b$file", a, diff(a, b), 1)
    if (patch.isEmpty) "" else patch.iterator().asScala.mkString("\n")
  }

}
