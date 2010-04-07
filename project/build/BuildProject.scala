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

    "org.scala-tools.sbt" %% "launcher-interface" % "0.7.3" % "provided"
    //"org.scala-tools.sbt" % "sbt-launch" % "0.7.2" % "provided"
  )

  lazy val hi = task { println(dependencies); None }

  // publishing (see http://code.google.com/p/simple-build-tool/wiki/Publishing)
  override
  def pomExtra = {
    <inceptionYear>2010</inceptionYear>
    <url>http://github.com/davidB/{projectName.value}</url>

    <licenses>
      <license>
        <name>CC-GNU LGPL 2.1</name>
        <url>http://creativecommons.org/licenses/LGPL/2.1/</url>
        <distribution>repo</distribution>
      </license>
    </licenses>

    <organization>
      <name>Alchim31</name>
      <url>http://alchim31.net/</url>
    </organization>
    <developers>
      <developer>
        <id>david.bernard</id>
        <name>David Bernard</name>
        <timezone>GMT+1</timezone>
        <roles>
          <role>Developer</role>
        </roles>
      </developer>
    </developers>

    <scm>
      <connection>scm:git:git://github.com/davidB/{projectName.value}.git</connection>
      <url>http://github.com/davidB/{projectName.value}/</url>
    </scm>
  }

  override
  def managedStyle = ManagedStyle.Maven

  val publishTo = "Sonatype OSSRH" at (isSnapshot match {
    case true => "http://oss.sonatype.org/content/repositories/github-snapshots/"
    case false => "http://oss.sonatype.org/service/local/staging/deploy/maven2"
  })

  def isSnapshot = projectVersion.value match {
    case OpaqueVersion(v) => v.endsWith("-SNAPSHOT")
    case BasicVersion(major, minor, micro, extra) => extra.map(_ == "SNAPSHOT").getOrElse(false)
  }

  Credentials(Path.userHome / ".ivy2" / ".credentials", log)

  override
  def packageDocsJar = defaultJarPath("-javadoc.jar")
  val docsArtifact = Artifact(artifactID, "docs", "jar", Some("javadoc"), Nil, None)

  override
  def packageSrcJar= defaultJarPath("-sources.jar")
  val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)

  override
  def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
}
