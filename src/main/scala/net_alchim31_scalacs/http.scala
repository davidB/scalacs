package net_alchim31_scalacs

import org.eclipse.jetty.server.handler.AbstractHandler

/**
 * call eg : curl --url http://localhost:27616/createOrUpdate --data-binary @sample2.yaml;type=text/x-yaml
 */
object HttpServer {
  def main(args : Array[String], joining : Boolean) : Unit = {
    import org.eclipse.jetty.server.Server

    val server = new Server(27616)
    server.setHandler(new CompilerHandler())

    server.start()
    if (joining) {
      server.join()
    }
  }

  def main(args : Array[String]) : Unit = main(args, true)
}

class CompilerHandler extends AbstractHandler {
  import org.eclipse.jetty.server.Request
  import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
  import java.io._
  import org.eclipse.jetty.http.HttpStatus

  private
  val _compilerSrv = new CompilerService4Group()

  override
  def handle(target : String, baseRequest : Request, request : HttpServletRequest, response : HttpServletResponse)  {
    response.setContentType("text/html;charset=utf-8")
    try {
      val beginAt = System.currentTimeMillis
      val eventCollector = new EventCollector(Nil)
      val customStatus = action(eventCollector, request)
      new EventLogger(Nil, eventCollector).info("time to process :" + ((System.currentTimeMillis - beginAt).toFloat / 1000) + "s")
      val txt = eventCollector.toCharSequence(true)
      customStatus match {
        case None => sendText(response, txt)
        case Some(status) => sendError(response, status, txt)
      }
    } catch {
      case t => sendError(response, HttpStatus.Code.INTERNAL_SERVER_ERROR, t)
    }
    baseRequest.setHandled(true)
  }

  //TODO add time need to process request
  def action(eventCollector : EventCollector, request : HttpServletRequest) : Option[HttpStatus.Code] = {
    request.getPathInfo.split('/').toList.filter(_.length != 0) match {
      case Nil => {
        new EventLogger(Nil, eventCollector).info("""usage :
          /ping : reply 'pong'
          /mem : display memory information
          /createOrUpdate : create or update project (send definition as yaml in content of the request)
                 ex : 'curl http://127.0.0.1:27616/createOrUpdate --data-binary @sample.yaml'
          /remove?p=xxx : remove a project xxx (xxx is a regular expression)
          /reset : reset compilers
          /clean : clean compilers
          /compile : run compilation on every project (else append ?p=xxx  where xxx is a regular expression use to find project by name)
        """)
        None
      }
      case actions :: _ => {
        actions.split(Array(' ','+')).foldLeft(None : Option[HttpStatus.Code]){ (cumul, i) =>
          cumul match {
            case Some(_) => cumul
            case None => action(eventCollector, i, request)
          }
        }
      }
    }
  }

  //TODO refactor to in/out eventCollector
  def action(eventCollector : EventCollector, act : String, request : HttpServletRequest) : Option[HttpStatus.Code] = {
    import java.util.regex.Pattern
    def findProject() : Option[Pattern] = {
      request.getParameterValues("p") match {
        case null => None
        case l : Array[String]  if l.length == 1 && l(0) != null && !l(0).isEmpty => Some(Pattern.compile(l(0)))
        case _ => None
      }
    }

    val actLog = new EventLogger(List(act), eventCollector)
    act match {
      case "stop" => {
        actLog.warn("stopping") //never reach
        new Thread(){
          override
          def run() {
            getServer.stop()
          }
        }.start()
        None
      }
      case "ping" => {
        actLog.info("pong") //TODO return the version of the scala compiler (later provide info about tools)
        None
      }
      case "mem" => {
        val runtime = Runtime.getRuntime()
        val txt = ("total memory = "+ runtime.totalMemory
          + ", max memory = " + runtime.maxMemory
          + ", free memory = " + runtime.freeMemory
        )
        actLog.info(txt)
        None
      }
      case "createOrUpdate" /*if "text/x-yaml" == contentType*/ => {
        import scala.collection.jcl.Conversions._
        import org.yaml.snakeyaml.Yaml
        val before = _compilerSrv.size
        // mime-type should be text/x-yaml (see http://stackoverflow.com/questions/332129/yaml-mime-type)
        var txt = ""
        val yaml = new Yaml()
        var counter = 0
        val iStream = request.getInputStream
        try {
          val it = yaml.loadAll(iStream).iterator
          while (it.hasNext) {
            val data = it.next().asInstanceOf[java.util.Map[String,_]]
            def toStr(k : String, orElse : String) : String = {
              data.get(k) match {
                case null => orElse
                case v => v.toString
              }
            }
            def toStrList(k : String) : List[String] = {
              data.get(k) match {
                case null => Nil
                case v => v.asInstanceOf[java.util.List[String]].toList
              }
            }
            def toFileOption(k : String) : Option[File] = {
              data.get(k) match {
                case null => None
                case v => Some(new File(v.toString))
              }
            }
            val cfg = new SingleConfig(
              toStr("name", "xxx"),
              toStrList("sourceDirs").map(new File(_)),
              toStrList("includes").map(RegExpUtil.globToRegexPattern(_)),
              toStrList("excludes").map(RegExpUtil.globToRegexPattern(_)),
              new File(toStr("targetDir", "/tmp/target")),
              toStrList("classpath").map(new File(_)),
              toStrList("args"),
              toFileOption("exported")
            )
            _compilerSrv.createOrUpdate(new CompilerService4Single(cfg, Some(_compilerSrv)))
            actLog.info("createdOrUpdated " + data + " :: " + cfg)
            counter += 1
          }
        } finally {
          if (iStream != null) {
            iStream.close()
          }
        }
        val after = _compilerSrv.size
        actLog.info("nb compiler created/updated/total : " + (after - before) + "/" + (counter - (after - before)) + "/" + _compilerSrv.size)
        None
      }
      case "remove" => {
        val before = _compilerSrv.size
        findProject() match {
          case Some(p) => _compilerSrv.removeByName(p)
          case None => actLog.warn("no project (name pattern) provided in queryString (p=...)")
        }
        val after = _compilerSrv.size
        actLog.info("nb compiler removed/total : " + (before - after) + "/" + after)
        None
      }
      // case "/add" => {
      //   Right("only compiler definition in 'text/x-yaml' are supported, your mime-type : " + contentType)
      // }
      case "reset" => {
        _compilerSrv.reset(actLog)
        None
      }
      case "clean" => {
        _compilerSrv.clean(actLog)
        None
      }
      case "compile" => {
        val str = new StringBuilder()
        val runId = System.currentTimeMillis
        val fbs = findProject() match {
          case Some(p) => _compilerSrv.compileByName(p, runId, true, Nil)
          case None => _compilerSrv.compile(runId, true, Nil)
        }
        for (fb <- fbs) {
          eventCollector.addAll(fb.events)
        }
        None
      }
      case unknown => {
        actLog.warn("unsupported operation : " + unknown)
        println("unsupported operation : " + unknown) //TODO use serverLogger
        Some(HttpStatus.Code.BAD_REQUEST)
      }
    }
  }

  private
  def sendText(response : HttpServletResponse, txt : CharSequence) {
    response.setContentType("text/plain; charset=UTF-8")
    //response.setContentLength(txt.length)
    response.getWriter().println(txt.toString())
    response.setStatus(HttpStatus.OK_200)
  }

  private
  def sendError(response : HttpServletResponse, status : HttpStatus.Code, exc : Throwable) {
    exc.printStackTrace();
    val writer = new java.io.StringWriter()
    exc.printStackTrace(new java.io.PrintWriter(writer))
    writer.close()
    sendError(response, status, writer.toString)
  }

  private
  def sendError(response : HttpServletResponse, status : HttpStatus.Code, txt : CharSequence) {
    response.setContentType("text/plain; charset=UTF-8")
    val writer = response.getWriter
    writer.print("Failure: ")
    writer.println(status)
    writer.println(txt)
    response.setStatus(status.getCode)
  }
}
