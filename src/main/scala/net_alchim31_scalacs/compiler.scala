package net_alchim31_scalacs

import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.lang.{Runtime, System, Thread}

import scala.tools.nsc.{Global, Settings,OfflineCompilerCommand}
import scala.tools.nsc.reporters.{Reporter}
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

///////////////////////////////////////////////////////////////////////////////
// API
///////////////////////////////////////////////////////////////////////////////

trait CompilerService {
  def reset(log : Logger)
  def clean(log : Logger)
  def compile(runId : Long, checkDeps : Boolean, previousFeedback : List[CompileFeedback]) : List[CompileFeedback]
}

class CompileFeedback (
  val runId : Long,
  val projectName : String,
  val events : EventCollector,
  val isChanged : Boolean,
  val isSuccess : Boolean
) {
//  def +(o : CompileFeedback) = {
//    if (runId != o.runId) {
//      throw new IllegalArgumentException("can't add CompileFeedback from differend runId :" + runId + " != " + o.runId)
//    }
//    new CompileFeedback(
//      runId,
//      events + o.events,
//      isChanged || o.isChanged,
//      isSuccess && o.isSuccess
//    )
//  }
}

class CompilerService4Group extends CompilerService {

  // manage group
  private
  var _group : List[CompilerService4Single] = Nil

  def createOrUpdate(v : CompilerService4Single) : this.type = {
    _group = v :: _group.filter(_.cfg.name != v.cfg.name)
    //TODO update dependency-link to avoid doing it each time via findByExported/findByTargetDir
    this
  }

  def removeByName(p : Pattern) : this.type = {
    _group = _group.filter(i => !p.matcher(i.cfg.name).matches())
    //TODO update dependency-link to avoid doing it each time via findByExported/findByTargetDir
    this
  }

  def size = _group.size

  def findByName(p : Pattern) = _group.filter(i => p.matcher(i.cfg.name).matches())

  def findByTargetDir(f : File) = {
    val apath = f.getCanonicalPath
    _group.find(i => apath == i.cfg.targetDir.getCanonicalPath)
  }

  def findByExported(f : File) = {
    val apath = f.getCanonicalPath
    _group.find(i => i.cfg.exported.map( apath == _.getCanonicalPath).getOrElse(false))
  }

  // dispatching
  def reset(log : Logger) =  _group.foreach { i => i.reset(log.newChild(i.cfg.name)) }
  def clean(log : Logger) = _group.foreach { i => i.clean(log.newChild(i.cfg.name)) }
  def compile(runId : Long, checkDeps : Boolean, previousFeedback : List[CompileFeedback]) = compile(_group, runId, checkDeps, previousFeedback)
  def compileByName(p : Pattern, runId : Long, checkDeps : Boolean, previousFeedback : List[CompileFeedback]) = compile(findByName(p), runId, checkDeps, previousFeedback)

  private
  def compile(group : List[CompilerService4Single], runId : Long, checkDeps : Boolean, previousFeedback : List[CompileFeedback]) = group.foldLeft(previousFeedback){(cumul, i) =>
    i.compile(runId, checkDeps,cumul)
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

///////////////////////////////////////////////////////////////////////////////
// Implementation who use scalac
///////////////////////////////////////////////////////////////////////////////
class CompilerService4Single(val cfg : SingleConfig, val allCompilerService : Option[CompilerService4Group]) extends CompilerService {

  val MaxCharge = 0.8

  private
  var _compiler: Global = null

  private def settingsAreCompatible(s1: Settings, s2: Settings) = s1 == s2

  private val runtime = Runtime.getRuntime()

  //TODO service should be provide by an external (to all several implementation, raw (full scan), based of FS notification,...)
  private
  def findFilesToCompile(lastCompileTime : Long) : List[File] = {
    def accept(file : File, rpath : String) : Boolean = {
      //TODO also recompile dependens file (try to use code from SBT)
      ( cfg.sourceExcludes.forall(pattern => !pattern.matcher(rpath).matches())
        && cfg.sourceIncludes.forall(pattern => pattern.matcher(rpath).matches())
      )
    }

    def findCompilables(rootDir : File, rpath : String) : List[File] = {
      var back : List[File] = Nil
      val dir = new File(rootDir, rpath)
      if (dir.isDirectory) {
        for (child <- dir.listFiles(); if !child.isHidden && child.canRead) {
          val rpathChild = rpath + "/" + child.getName
          if (child.isDirectory) {
            back = findCompilables(rootDir, rpathChild) ::: back
          } else if (accept(child, rpathChild)) {
            back = child :: back
          }
        }
      }
      back
    }

    val compilables = cfg.sourceDirs.flatMap(s => findCompilables(s, ""))
    compilables.exists(_.lastModified() >= lastCompileTime) match {
      case true => compilables.sort(_.getAbsolutePath < _.getAbsolutePath) //sort files to have a reproductible build across host
      case false => Nil
    }
  }

  def reset(log : Logger) = {
    _compiler = null
    log.info("compile server was reset")
  }

  def clean(log : Logger) = {
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
    log.info("deleted classes into " + cfg.targetDir)
  }

  var _lastCompileFeedback = new CompileFeedback(
    java.lang.Long.MIN_VALUE,
    cfg.name,
    new EventCollector(),
    false,
    true
  )


  //TODO update reporter from compiler to use a new out for each action
  //TODO use the GraphNode mixin
  //TODO use LoggerMessage structure instead of CharSequence to store status, messages,...
  def compile(runId : Long, checkDeps : Boolean, previousFeedback : List[CompileFeedback]) : List[CompileFeedback] = {
    import java.io.{PrintWriter, StringWriter, StringReader, BufferedReader}

    if (runId <= _lastCompileFeedback.runId) {
      throw new Exception("old runId ('" + runId +"') requested after '" + _lastCompileFeedback.runId +"'")
    }
    if (runId == _lastCompileFeedback.runId) {
      return _lastCompileFeedback :: previousFeedback.filter(_.projectName != _lastCompileFeedback.projectName)
    }

    var back = previousFeedback
    val eventCollector = new EventCollector()
    val eventLogger = new EventLogger(List("compiler", cfg.name), eventCollector)
    var isChanged = false
    var isSuccess = true


    // request uptodate dependencies :
    //TODO avoid cycle
    val classpath = allCompilerService match {
      case None => cfg.classpath
      case Some(acs) => cfg.classpath.map { f =>
        val oPrj = acs.findByExported(f) orElse acs.findByTargetDir(f)
        oPrj match {
          case Some(prj) if (prj == this) => f
          case Some(prj) => {
            back = prj.compile(runId, checkDeps, back)
            val fb = back.head
            if (!fb.isSuccess) {
              eventLogger.error("can't recompil due to failure in compilation of " + fb.projectName)
              isSuccess = false
            } else {
              isChanged = isChanged || fb.isChanged
            }
            // use targetDir in classpath
            prj.cfg.targetDir
          }
          case None => (f.lastModified() > _lastCompileFeedback.runId) match {
            case true => {
              eventLogger.info("dependency file modified : " + f.getCanonicalPath)
              isChanged = true
              f
            }
            case false => f
          }
        }
      }
    }
    if (!isSuccess) {
      _lastCompileFeedback = new CompileFeedback(runId, cfg.name, eventCollector, isChanged, isSuccess)
      return _lastCompileFeedback :: back
    }


    if (!cfg.targetDir.exists()) {
      cfg.targetDir.mkdirs();
    }

    // to be compatible with maven
    val lastCompileAtFile = new File(cfg.targetDir + ".timestamp");
    val lastCompileAt : Long = if (lastCompileAtFile.exists() && (cfg.targetDir.list().length > 0)) {
      lastCompileAtFile.lastModified()
    } else {
      -1
    }

    val now = System.currentTimeMillis()
    val classpathIsChanged = isChanged //back.foldLeft(false)((r,i) => r || i.isChanged)
    val files = classpathIsChanged match {
      case false => findFilesToCompile(lastCompileAt)
      case true => reset(eventLogger) ; findFilesToCompile(java.lang.Long.MIN_VALUE)
    }

    if (files.isEmpty) {
      eventLogger.info("Nothing to compile - all classes are up to date")
      _lastCompileFeedback = new CompileFeedback(runId, cfg.name, eventCollector, isChanged, isSuccess)
      return _lastCompileFeedback :: back
    }

//....
    try {
      val args = cfg.additionalArgs :::
        List(
          "-d", cfg.targetDir.getAbsolutePath,
          "-classpath", (cfg.targetDir :: cfg.classpath).map(_.getAbsolutePath).mkString(File.pathSeparator)
        ) :::
        cfg.sourceDirs.flatMap(f => List("-sourcepath", f.getAbsolutePath)) :::
        files.map(_.getAbsolutePath)

      eventLogger.info("Compiling " + files.length + " source files from " + cfg.targetDir)

      val command = new OfflineCompilerCommand(args, new Settings(eventLogger.error), eventLogger.error, false)
      val reporter = new CompilerLoggerAdapter(command.settings, eventLogger)
      lazy val newGlobal = new Global(command.settings, reporter) {
        override def inform(msg: String) = eventLogger.info(msg)
      }
      //TODO update code to avoid usage of reporter
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
            eventLogger.info("Starting new compile server instance")
            _compiler = newGlobal
          }
          val c = _compiler
          val run = new c.Run
          run compile command.files


          //TODO if there is error compilation don't update time stamp to recompile file after (may be need a fix in an other file)
          if (isSuccess) {
            if (!lastCompileAtFile.exists()) {
              new java.io.FileOutputStream(lastCompileAtFile).close()
            }
            lastCompileAtFile.setLastModified(now)
          }
        } catch {
          case ex => {
//            if (command.settings.debug.value) {
//              ex.printStackTrace(out)
//            }
            eventLogger.error("fatal error", ex)
            reset(eventLogger)
          }
        }
      }
//      reporter.printSummary()
      runtime.gc()
      if ((runtime.totalMemory() - runtime.freeMemory()).toDouble / runtime.maxMemory().toDouble > MaxCharge) {
        eventLogger.info("memory load > " + MaxCharge)
        reset(eventLogger)
      }
    } catch {
      case t => {
        isSuccess = false
        t.printStackTrace()
        eventLogger.error("exception", t)
      }
    }
    _lastCompileFeedback = new CompileFeedback(runId, cfg.name, eventCollector, isChanged, isSuccess)
    _lastCompileFeedback :: back
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
