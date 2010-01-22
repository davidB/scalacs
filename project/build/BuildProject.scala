import sbt._

class BuildProject(info: ProjectInfo) extends DefaultProject(info)
{
/*
  override val dependencies = List(
	"org.jboss.netty" % "netty" % "3.1.0.GA" % "compile->default",
   	"org.scala-lang" % "scala-compiler" % "2.7.5",
	"junit" % "junit" % "4.5" % "test->default",
	"SnakeYAML" % "SnakeYAML" % "1.3"
  )
*/

  val netty = "org.jboss.netty" % "netty" % "3.1.0.GA" % "compile->default"
  val scala_compiler = "org.scala-lang" % "scala-compiler" % "2.7.5"
  val junit = "junit" % "junit" % "4.5" % "test->default"
  val snakeyaml = "SnakeYAML" % "SnakeYAML" % "1.3"

  // required because Ivy doesn't pull repositories from poms
  val jbossRepo = "m2-repository-jboss" at "http://repository.jboss.org/maven2"
  val snakeRepo = "m2-repository-snakeyaml" at "http://snakeyamlrepo.appspot.com/repository"

  lazy val hi = task { println(dependencies); None }

}
