package org.scala_tools.server

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

trait CompilerService {
  def reset() : CharSequence
  def clean() : CharSequence
  def compile() : CharSequence
}

class CompilerService4Group extends CompilerService {
  private
  var _group : List[CompilerService] = Nil

  def add(v : CompilerService) : this.type = {
    // should generate new name if name already exist or replace existing ??
    _group = v :: _group
    this
  }
  def size = _group.size

  // dispatching
  def reset() : CharSequence = _group.foldLeft("")((r,i) => r + i.reset().toString)
  def clean() : CharSequence = _group.foldLeft("")((r,i) => r + i.clean().toString)
  def compile() : CharSequence = _group.foldLeft("")((r,i) => r + i.compile().toString)
}

class SingleConfig (
  val name : String,
  val sourceDirs : List[File],
  val sourceIncludes : List[Pattern],
  val sourceExcludes : List[Pattern],
  val targetDir : File,
  val classpath : List[File],
  val additionalArgs : List[String]
)

class CompilerService4Single(val cfg : SingleConfig) extends CompilerService {

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
    def delete(f : File) {
      if (f.isDirectory) {
        for (f <- f.listFiles()) {
          delete(f)
        }
      }
      f.delete()
    }
    delete(cfg.targetDir)
    "[Compile server has deleted " + cfg.targetDir + "]\n"
  }

  //TODO update reporter from compiler to use a new out for each action
  def compile() :  CharSequence = {
    import java.io.{PrintWriter, StringWriter, StringReader, BufferedReader}

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
    val files = findFilesToCompile(lastCompileAt)

    if (files.isEmpty) {
      return "Nothing to compile - all classes are up to date\n"
    }

    val back = new StringWriter()
    val out = new PrintWriter(back)
    val in = new BufferedReader(new StringReader(""))

    try {
      def error(msg: String) {
        out.println(/*new Position*/ FakePos("fsc"), msg + "\n  fsc -help  gives more information")
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
      val reporter = new ConsoleReporter(command.settings, in, out) {
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

