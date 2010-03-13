package net_alchim31_scalacs

//TODO add test with the regexp to parse Compilation error (may be include the parser into BasicHttpScalacsClientj.sh
class RequestCompileTest {
  import java.net.URLClassLoader
  import java.io.File
  import org.testng.Assert
  import org.testng.annotations.{AfterClass, Test}
  import scala.collection.jcl.Conversions._

  def trace(str : String) = { /* println(str) */ }
  
  private lazy
  val _client = {
    import net_alchim31_scalacs_client.BasicHttpScalacsClient
    new BasicHttpScalacsClient() {
      import java.net.URL
      def startNewServer() {
        HttpServer.main(Array[String](), false)
      }
      override
      def traceUrl(url : URL) {
        trace("request : " + url)
      }
    }
  }

  private
  val _prjDir = System.getProperty("project.basedir", System.getProperty("user.dir"))
  private
  val _samplesClassesRootDir = new File(System.getProperty("samplesClassesRootDir", _prjDir +"/target/samples-classes"))
  private
  val _samplesSrcRootDir = new File(System.getProperty("samplesSrcRootDir", _prjDir +"/src/test/samples"))
  private
  val _scalaLibPath = {
    val urls = Thread.currentThread.getContextClassLoader.asInstanceOf[URLClassLoader].getURLs
    //val urls = this.getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs
    trace(urls.mkString("[\n", "\n", "\n]"))
    urls.filter(_.getPath.contains("scala-library")).firstOption.map(_.getPath).getOrElse{
      throw new IllegalStateException("scala-library not found")
    }
  }

  @Test
  def compileHelloOkWithoutScalaLibShouldFailed() {
    val sampleName = "prj-hello-ok" //-WithoutScalaLibShouldFailed"

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
    """,
      sampleName,
      new File(_samplesSrcRootDir, sampleName).getCanonicalPath,
      new File(_samplesClassesRootDir, sampleName).getCanonicalPath
    ));
    trace(createReturn)
    Assert.assertTrue(createReturn.contains("created/updated/total : 1/0/1"), "one project added")
    // request compile
    try {
      import net_alchim31_scalacs_client.BasicHttpScalacsClient.Level
      
      val compileReturn = _client.sendRequestCompile()
      trace(compileReturn)
      val r = _client.parse(compileReturn);
      Assert.assertTrue(r.size > 0, "has some feedback lines")
      val errors = r.filter(_.level == Level.ERROR)
      Assert.assertTrue(errors.size == 1, "one project added")
      Assert.assertTrue(errors.first.text.toString.contains("object scala not found"), "has some feedback lines")
    } finally {
      val removeReturn = _client.sendRequestRemove(sampleName);
      trace(removeReturn)
      Assert.assertTrue(removeReturn.contains("removed/total : 1/0"), "has some feedback lines")
    }
  }

  @Test
  def compileHelloOk() {
    val sampleName = "prj-hello-ok"
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
      new File(_samplesSrcRootDir, sampleName).getCanonicalPath,
      new File(_samplesClassesRootDir, sampleName).getCanonicalPath,
      //urls.map('"' + _.getPath + '"').mkString("\n  - ")
      _scalaLibPath
    ));
    trace(createReturn)
    Assert.assertTrue(createReturn.contains("created/updated/total : 1/0/1"), "has some feedback lines")
    try {
      // request compile
      val compileReturn = _client.sendRequestCompile()
      trace(compileReturn)
      Assert.assertFalse(compileReturn.contains("-ERROR\t"), "has some feedback lines")
    } finally {
      val removeReturn = _client.sendRequestRemove(sampleName);
      trace(removeReturn)
      Assert.assertTrue(removeReturn.contains("removed/total : 1/0"), "has some feedback lines")
    }
  }

  @Test
  def compileHelloFailedCompile() {
    val sampleName = "prj-hello-failed-compile" //-WithoutScalaLibShouldFailed"
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
      new File(_samplesSrcRootDir, sampleName).getCanonicalPath,
      new File(_samplesClassesRootDir, sampleName).getCanonicalPath,
      _scalaLibPath
    ));
    trace(createReturn)
    Assert.assertTrue(createReturn.contains("created/updated/total : 1/0/1"), "has some feedback lines")
    // request compile
    try {
      val compileReturn = _client.sendRequestCompile()
      trace(compileReturn)
      Assert.assertTrue(compileReturn.contains("-ERROR\t"), "has some feedback lines")
      Assert.assertTrue(compileReturn.contains("')' expected but '}' found"), "has some feedback lines")
    } finally {
      val removeReturn = _client.sendRequestRemove(sampleName);
      trace(removeReturn)
      Assert.assertTrue(removeReturn.contains("removed/total : 1/0"), "has some feedback lines")
    }
  }

  //@AfterClass(alwaysRun = true )
  @AfterClass
  def stopServer() {
    //_client.sendRequestStop()
  }
}