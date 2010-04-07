import sbt._

class BuildProject(info: ProjectInfo) extends DefaultProject(info) {

  override
  def libraryDependencies = Set(
    "org.eclipse.jetty" % "jetty-server" % "7.0.2.RC0",
    "org.yaml" % "snakeyaml" % "1.4",
    "org.scala-lang" % "scala-compiler" % "2.7.7",

    "org.testng" % "testng" % "5.11" % "test->default" classifier "jdk15",
    "commons-io" % "commons-io" % "1.3.2" % "test->default",
    "commons-lang" % "commons-lang" % "2.4" % "test->default",

    "org.scala-tools.sbt" % "launcher-interface" % "0.7.3" % "provided"
    //"org.scala-tools.sbt" % "sbt-launch" % "0.7.2" % "provided"
  )

  lazy val hi = task { println(dependencies); None }

}
