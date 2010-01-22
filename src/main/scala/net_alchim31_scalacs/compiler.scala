package net_alchim31_scalacs

import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.lang.{Runtime, System, Thread}

import scala.tools.nsc.{Global, Settings,OfflineCompilerCommand}
import scala.tools.nsc.reporters.{Reporter, ConsoleReporter}
import scala.tools.nsc.util.FakePos //Position
import java.io.File
import java.util.regex.{Pattern, PatternSyntaxException}

/**
 * @author David Bernard
 * @based on scala.tools.nsc.StandardCompileServer
 */
//TODO use actor/queue to run command once after once
//TODO define CompilationGroup as Trait with Composite and Single implementation
//TODO find a better name (eg, Builder, CompilerService)

class CompileFeedback(
  val runId : Long,
  val out : CharSequence,
  val isChanged : Boolean,
  val isSuccess : Boolean
) {
  def +(o : CompileFeedback) = {
    if (runId != o.runId) {
      throw new IllegalArgumentException("can't add CompileFeedback from differend runId :" + runId + " != " + o.runId)
    }
    new CompileFeedback(
      runId,
      out.toString + o.out.toString,
      isChanged || o.isChanged,
      isSuccess && o.isSuccess
    )
  }
}

trait CompilerService {
  def reset() : CharSequence
  def clean() : CharSequence
  def compile(runId : Long, checkDeps : Boolean) : List[CompileFeedback]
}

class CompilerService4Group extends CompilerService {
  private
  var _group : List[CompilerService4Single] = Nil

  def add(v : CompilerService4Single) : this.type = {
    // should generate new name if name already exist or replace existing ??
    _group = v :: _group.filter(_.cfg.name == v.cfg.name)
    this
  }

  def remove(v : CompilerService4Single) : this.type = {
    // should generate new name if name already exist or replace existing ??
    _group = _group.filter(_.cfg.name == v.cfg.name)
    this
  }

  def size = _group.size

  // dispatching
  def reset() : CharSequence = _group.foldLeft("")((r,i) => r + i.reset().toString)
  def clean() : CharSequence = _group.foldLeft("")((r,i) => r + i.clean().toString)
  def compile(runId : Long, checkDeps : Boolean) = _group.flatMap(_.compile(runId, checkDeps))//.removeDuplicates()

  def findByName(p : Pattern) = _group.find(i => p.matcher(i.cfg.name).matches())
  def findByTargetDir(f : File) = {
    val apath = f.getCanonicalPath
    _group.find(i => apath == i.cfg.targetDir.getCanonicalPath)
  }

  def findByExported(f : File) = {
    val apath = f.getCanonicalPath
    _group.find(i => i.cfg.exported.map( apath == _.getCanonicalPath).getOrElse(false))
  }

}

class SingleConfig (
  val name : String,
  val sourceDirs : List[File],
  val sourceIncludes : List[Pattern],
  val sourceExcludes : List[Pattern],
  val targetDir : File,
  val classpath : List[File],
  val additionalArgs : List[String],
  val exported : Option[File]
)

class CompilerService4Single(val cfg : SingleConfig, val allCompilerService : Option[CompilerService4Group]) extends CompilerService {

  val MaxCharge = 0.8

  private
  var _compiler: Global = null

  private def settingsAreCompatible(s1: Settings, s2: Settings) = s1 == s2

  private val runtime = Runtime.getRuntime()

  private
  def findFilesToCompile(lastCompileTime : Long) : List[File] = {
    def accept(file : File, rpath : String) : List[File] = {
      //TODO also recompile dependens file (try to use code from SBT)
      val ok = ( true //file.lastModified() >= lastCompileTime
        && cfg.sourceExcludes.forall(pattern => !pattern.matcher(rpath).matches())
        && cfg.sourceIncludes.forall(pattern => pattern.matcher(rpath).matches())
      )
      ok match {
        case true => List(file)
        case false => Nil
      }
    }

    def findFilesToCompile(rootDir : File, rpath : String) : List[File] = {
      val dir = new File(rootDir, rpath)
      var back : List[File] = Nil
      for (child <- dir.listFiles(); if !child.isHidden && child.canRead) {
        val rpathChild = rpath + "/" + child.getName
        if (child.isDirectory) {
          back = findFilesToCompile(rootDir, rpathChild) ::: back
        } else {
          back = accept(child, rpathChild) ::: back
        }
      }
      back
    }

    cfg.sourceDirs.flatMap(s => findFilesToCompile(s, ""))
  }

  def reset() : CharSequence = {
    _compiler = null
    "[Compile server was reset]\n"
  }

  def clean() : CharSequence = {
    def delete(f : File) : Boolean = {
      var back =  true
      if (f.isDirectory) {
        for (f <- f.listFiles()) {
          back = delete(f) && back
        }
      } else if (!f.getName.endsWith(".class")) {
        back = false
      }
      if (back) {
        f.delete()
      }
      back
    }
    delete(cfg.targetDir)
    "[Compile server has deleted classes into " + cfg.targetDir + "]\n"
  }

  var _lastCompileFeedback = new CompileFeedback(
    java.lang.Long.MIN_VALUE,
    "",
    false,
    true
  )


  //TODO update reporter from compiler to use a new out for each action
  //TODO use the GraphNode mixin
  //TODO use LoggerMessage structure instead of CharSequence to store status, messages,...
  def compile(runId : Long, checkDeps : Boolean) : List[CompileFeedback] = {
    import java.io.{PrintWriter, StringWriter, StringReader, BufferedReader}

    if (runId <= _lastCompileFeedback.runId) {
      throw new Exception("old runId ('" + runId +"') requested after '" + _lastCompileFeedback.runId +"'")
    }

    var back : List[CompileFeedback] = Nil

    if (runId == _lastCompileFeedback.runId) {
      return List(_lastCompileFeedback)
    }

    // request uptodate dependencies :
    val classpath = allCompilerService match {
      case None => cfg.classpath
      case Some(acs) => cfg.classpath.map { f =>
        acs.findByExported(f) match {
          case Some(prj) =>  back = back ::: prj.compile(runId, checkDeps); prj.cfg.targetDir
          case None => acs.findByTargetDir(f) match {
            case Some(prj) =>  back = back ::: prj.compile(runId, checkDeps); prj.cfg.targetDir
            case None => (f.lastModified() > _lastCompileFeedback.runId) match {
              case true => back = new CompileFeedback(runId, "file " + f +" updated since last compile", true, true) :: back; f
              case false => f
            }
          }
        }
      }
    }

    if (!cfg.targetDir.exists()) {
      cfg.targetDir.mkdirs();
    }

    val lastCompileAtFile = new File(cfg.targetDir + ".timestamp");
    val lastCompileAt : Long = if (lastCompileAtFile.exists() && (cfg.targetDir.list().length > 0)) {
      lastCompileAtFile.lastModified()
    } else {
      -1
    }

    val now = System.currentTimeMillis()
    val classpathIsChanged = back.foldLeft(false)((r,i) => r || i.isChanged)
    val files = classpathIsChanged match {
      case false => findFilesToCompile(lastCompileAt)
      case true => reset() ; findFilesToCompile(java.lang.Long.MIN_VALUE)
    }

    if (files.isEmpty) {
      return new CompileFeedback(
        runId,
        "Nothing to compile - all classes are up to date",
        false,
        true
      ) :: back
    }
//....
    val outStr = new StringWriter()
    val out = new PrintWriter(outStr)

    try {
      def error(msg: String) {
        out.println(/*new Position*/ FakePos("hsc"), msg + "\n  hsc -help  gives more information")
      }

      val args = cfg.additionalArgs :::
        List(
          "-d", cfg.targetDir.getAbsolutePath,
          "-classpath", (cfg.targetDir :: cfg.classpath).map(_.getAbsolutePath).mkString(File.pathSeparator)
        ) :::
        cfg.sourceDirs.flatMap(f => List("-sourcepath", f.getAbsolutePath)) :::
        files.map(_.getAbsolutePath)

      out.println("Compiling " + files.length + " source files to " + cfg.targetDir)

      val command = new OfflineCompilerCommand(args, new Settings(error), error, false)
      val reporter = new CompilerReporter(command.settings, out) {
        // disable prompts, so that compile server cannot block
        override def displayPrompt = ()
      }
      lazy val newGlobal = new Global(command.settings, reporter) {
        override def inform(msg: String) = out.println(msg)
      }

      if (command.shouldStopWithInfo) {
        reporter.info(null, command.getInfoMessage(newGlobal), true)
      } else if (command.files.isEmpty) {
        //TODO display correct usage and not backend compiler
        reporter.info(null, command.usageMsg, true)
      } else {
        try {
          if (_compiler != null) { //&& settingsAreCompatible(command.settings, compiler.settings)) {
            _compiler.settings = command.settings
            _compiler.reporter = reporter
          } else {
            out.println("[Starting new compile server instance]")
            _compiler = newGlobal
          }
          val c = _compiler
          val run = new c.Run
          run compile command.files


          //TODO if there is error compilation don't update time stamp to recompile file after (may be need a fix in an other file)
          if (!lastCompileAtFile.exists()) {
            new java.io.FileOutputStream(lastCompileAtFile).close()
          }
          lastCompileAtFile.setLastModified(now)
        } catch {
          case ex => {
            if (command.settings.debug.value) {
              ex.printStackTrace(out)
            }
            reporter.error(null, "fatal error: " + ex.getMessage)
            out.println(reset())
          }
        }
      }
      reporter.printSummary()
      runtime.gc()
      if ((runtime.totalMemory() - runtime.freeMemory()).toDouble / runtime.maxMemory().toDouble > MaxCharge) {
        out.println("memory load > " + MaxCharge)
        out.println(reset())
      }
    } catch {
      case t => t.printStackTrace(out)
    } finally {
      out.close()
      in.close()
    }
    back.close()
    back.toString()
  }

  private
  def normalize(f : File) = {
    try {
      f.getCanonicalFile()
    } catch {
      case _ => f.getAbsoluteFile()
    }
  }
}

abstract class Projects {

  /**
   * Update the definition of existing project with the same name
   * Update projects dependency graph
   */
  def add(v : ProjectRawInfo)

  /**
   * visit the list of project sorted by dependency
   */
  def visit(f : ProjectRawInfo => Unit)
}

case class VFile(f : File, link : Option[File])
class VFileList extends immutable.List[VFile] {
  def realPath : List[File] = map{ vf =>
    vf.link.filter(_.exists).getOrElse(vf.f)
  }

  def realPathStr = realPath.map(f.getCanonicalPath).mkString(File.separatorPath)

  def update(vf : VFile) = {
    (head.f == vf.f) match {
      case true => vf :: tail
      case false => head :: update(tail)
    }
  }
}

trait TypeInfo {
  def src : File
  def dotclass : File
  def directDep : Set[TypeName]
}

class SrcDependencyTools {
  def srcToClass(src : File)
  def classToClassFile
}

