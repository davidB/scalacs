package net_alchim31_scalacs

class RequestCompileTest {
  import org.junit.{Before, Test}
  import java.net.URLClassLoader

  def trace(str : String) = {}
  private lazy
  val _client = {
    import net_alchim31_scalacs_client.BasicHttpScalacsClient
    new BasicHttpScalacsClient() {
      import java.net.URL
      def startNewServer() {
        HttpServer.main(Array[String]())
      }
      override
      def traceUrl(url : URL) {
        trace("request : " + url)
      }
    }
  }

  @Test
  def compileHelloOkWithoutScalaLibShouldFailed() {
    import java.io.File
    import org.junit.Assert
    val sampleName = "prj-hello-ok" //-WithoutScalaLibShouldFailed"
    val prjDir = System.getProperty("project.basedir", System.getProperty("user.dir"))
    val samplesSrcRootDir = new File(System.getProperty("samplesSrcRootDir", prjDir +"/src/test/samples"))
    val samplesClassesRootDir = new File(System.getProperty("samplesClassesRootDir", prjDir +"/target/samples-classes"))
    // register project
    val createReturn = _client.sendRequestCreateOrUpdate(String.format("""
name : %s
sourceDirs :
  - "%s"
includes :
  - "*.scala"
excludes :
targetDir : "%s"
classpath :
args :
    """, sampleName, new File(samplesSrcRootDir, sampleName).getCanonicalPath, new File(samplesClassesRootDir, sampleName).getCanonicalPath));
    trace(createReturn)
    Assert.assertTrue("one project added", createReturn.contains("created/updated/total : 1/0/1"))
    // request compile
    val compileReturn = _client.sendRequestCompile()
    trace(compileReturn)
    Assert.assertTrue("has Error", compileReturn.contains("-ERROR\t"))
    Assert.assertTrue("has good error message", compileReturn.contains("object scala not found"))
    val removeReturn = _client.sendRequestRemove(sampleName);
    trace(removeReturn)
    Assert.assertTrue("every project removed", removeReturn.contains("removed/total : 1/0"))
  }

  @Test
  def compileHelloOk() {
    import java.io.File
    import org.junit.Assert
    import java.net.URLClassLoader
    val sampleName = "prj-hello-ok"
    val prjDir = System.getProperty("project.basedir", System.getProperty("user.dir"))
    val samplesSrcRootDir = new File(System.getProperty("samplesSrcRootDir", prjDir +"/src/test/samples"))
    val samplesClassesRootDir = new File(System.getProperty("samplesClassesRootDir", prjDir +"/target/samples-classes"))
    val urls = Thread.currentThread.getContextClassLoader.asInstanceOf[URLClassLoader].getURLs
    //val urls = this.getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs
    trace(urls.mkString("[\n", "\n", "\n]"))
    val scalaLibPath = urls.filter(_.getPath.contains("scala-library")).firstOption.map(_.getPath).getOrElse{
      throw new IllegalStateException("scala-library not found")
    }
    // register project
    val createReturn = _client.sendRequestCreateOrUpdate(String.format("""
name : %s
sourceDirs :
  - "%s"
includes :
  - "*.scala"
excludes :
targetDir : "%s"
classpath :
  - %s
args :
    """,
      sampleName,
      new File(samplesSrcRootDir, sampleName).getCanonicalPath,
      new File(samplesClassesRootDir, sampleName).getCanonicalPath,
      //urls.map('"' + _.getPath + '"').mkString("\n  - ")
      scalaLibPath
    ));
    trace(createReturn)
    Assert.assertTrue("one project added", createReturn.contains("created/updated/total : 1/0/1"))
    // request compile
    val compileReturn = _client.sendRequestCompile()
    trace(compileReturn)
    Assert.assertTrue("has no Error", !compileReturn.contains("-ERROR\t"))
    val removeReturn = _client.sendRequestRemove(sampleName);
    trace(removeReturn)
    Assert.assertTrue("every project removed", removeReturn.contains("removed/total : 1/0"))
  }

}