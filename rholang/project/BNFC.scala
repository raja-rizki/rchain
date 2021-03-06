import sbt._
import Keys._
import scala.sys.process._

object BNFC {

  lazy val BNFCConfig     = config("bnfc")
  lazy val bnfcNamespace  = settingKey[String]("Namespace to prepend to the package/module name")
  lazy val bnfcGrammarDir = settingKey[File]("Directory for BNFC grammar files")
  lazy val bnfcOutputDir  = settingKey[File]("Directory for Java files generated by BNFC")
  lazy val bnfcDocDir     = settingKey[File]("Directory for LaTeX files generated by BNFC")
  lazy val generate       = taskKey[Unit]("Generates Java files from BNFC grammar files")
  lazy val cleanDocs      = taskKey[Unit]("Cleans BNFC-generated LaTeX files")
  lazy val generateDocs   = taskKey[Unit]("Generates LaTeX files from BNFC grammar files")

  def cleanDir(dir: File): Unit =
    Process(s"rm -rf $dir") !

  def nsToPath(ns: String): String =
    ns.replaceAll("\\.", "/")

  def stripSuffix(filename: String): String =
    filename.split("\\.").head

  def makeOutputPath(grammarFile: File, outputDir: File, namespace: String): String =
    s"$outputDir/${nsToPath(namespace)}/${stripSuffix(grammarFile.getName)}"

  def bnfcGenerateSources(fullClasspath: Seq[Attributed[File]], grammarFile: File, outputDir: File, namespace: String): Unit = {
    val classpath: String = fullClasspath.map(e => e.data).mkString(":")
    val targPath: String  = makeOutputPath(grammarFile, outputDir, namespace)
    val bnfcCmd: String   = s"bnfc -l --java --jflex -o ${outputDir.getAbsolutePath} -p $namespace $grammarFile"
    val jlexCmd: String   = s"jflex $targPath/Yylex"
    val renameDefaultCmd: String = s"mv $targPath/_cup.cup $targPath/${stripSuffix(grammarFile.getName)}.cup"
    val cupCmd: String    = s"java -cp $classpath java_cup.Main -locations -expect 100 $targPath/${stripSuffix(grammarFile.getName)}.cup" // TODO: Figure out naming behind _cup.cup
    val mvCmd: String     = s"mv sym.java parser.java $targPath"
    Process(bnfcCmd) #&& Process(jlexCmd) #&& Process(renameDefaultCmd) #&& Process(cupCmd) #&& Process(mvCmd) !
  }

  def bnfcGenerateLaTeX(grammarFile: File, outputDir: File): Unit = {
    val bnfcCmd: String = s"bnfc --latex -o ${outputDir.getAbsolutePath} $grammarFile"
    Process(bnfcCmd) !
  }

  def bnfcFiles(base: File): Seq[File] = (base * "*.cf").get

  lazy val bnfcSettings = inConfig(BNFCConfig)(Defaults.configSettings ++ Seq(
    javaSource     := (javaSource in Compile).value,
    scalaSource    := (javaSource in Compile).value,
    bnfcNamespace  := "coop.rchain.syntax",
    bnfcGrammarDir := baseDirectory.value / "src" / "main" / "bnfc",
    bnfcOutputDir  := (javaSource in Compile).value,
    bnfcDocDir     := baseDirectory.value / "doc" / "bnfc",
    clean          := cleanDir(bnfcOutputDir.value / nsToPath(bnfcNamespace.value)),
    generate       := {
      val fullCP = (fullClasspath in BNFCConfig).value
      bnfcFiles(bnfcGrammarDir.value).foreach { (f: File) =>
        bnfcGenerateSources(fullCP, f, bnfcOutputDir.value, bnfcNamespace.value)
      }
    },
    cleanDocs      := cleanDir(bnfcDocDir.value),
    generateDocs   := bnfcFiles(bnfcGrammarDir.value).foreach { (f: File) =>
      bnfcGenerateLaTeX(f, bnfcDocDir.value)
    }))
}
