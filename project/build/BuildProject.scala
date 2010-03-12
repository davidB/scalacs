import sbt._

class BuildProject(info: ProjectInfo) extends DefaultProject(info)
{
/*
  override val dependencies = List(
	"org.jboss.netty" % "netty" % "3.1.0.GA" % "compile->default",
   	"org.scala-lang" % "scala-compiler" % "2.7.7",
	"junit" % "junit" % "4.5" % "test->default",
	"SnakeYAML" % "SnakeYAML" % "1.3"
  )
*/

  val netty = "org.jboss.netty" % "netty" % "3.1.5.GA"
  val snakeyaml = "org.yaml" % "snakeyaml" % "1.4"
  val scala_compiler = "org.scala-lang" % "scala-compiler" % "2.7.7"

  val junit = "junit" % "junit" % "4.5" % "test->default"
  val commons_io = "commons-io" % "commons-io" % "1.3.2" % "test->default"
  val commons_lang = "commons-lang" % "commons-lang" % "2.4" % "test->default"

  // required because Ivy doesn't pull repositories from poms
  val jbossRepo = "m2-repository-jboss" at "http://repository.jboss.org/maven2"

  lazy val hi = task { println(dependencies); None }

}
