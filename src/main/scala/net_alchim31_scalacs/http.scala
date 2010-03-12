package net_alchim31_scalacs

import java.net.InetSocketAddress
import org.yaml.snakeyaml.Yaml;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * call eg : curl --url http://localhost:27616/add --data-binary @sample2.yaml;type=text/x-yaml
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author david.bernard
 * @based on code from org.jboss.netty.example.http.file
 */
//TODO add start and stop method
object HttpServer {

    def main(args : Array[String]) {
      // Configure the server.
      val bootstrap = new ServerBootstrap(
        new NioServerSocketChannelFactory(
          Executors.newCachedThreadPool(),
          Executors.newCachedThreadPool()
        )
      )
      val port = 27616

      // Set up the event pipeline factory.
      bootstrap.setPipelineFactory(new HttpPipelineFactory())

      // Bind and start to accept incoming connections.
      println("start waiting request at "+ port) //TODO log
      bootstrap.bind(new InetSocketAddress(port))
    }
}

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.stream.ChunkedFile;

import org.jboss.netty.channel.ChannelPipelineFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author david.bernard
 * @based on code from org.jboss.netty.example.http.file
 */
class HttpPipelineFactory extends ChannelPipelineFactory {

  import org.jboss.netty.channel.Channels
  //import org.jboss.netty.handler.stream.ChunkedWriteHandler;
  import org.jboss.netty.channel.ChannelPipeline;
  import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
  import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
  private
  val _compilerSrv = new CompilerService4Group()

  def getPipeline() : ChannelPipeline = {
    // Create a default pipeline implementation.
    val pipeline = Channels.pipeline()

    // Uncomment the following line if you want HTTPS
    //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
    //engine.setUseClientMode(false);
    //pipeline.addLast("ssl", new SslHandler(engine));

    pipeline.addLast("decoder", new HttpRequestDecoder())
    pipeline.addLast("encoder", new HttpResponseEncoder())
    //pipeline.addLast("chunkedWriter", new ChunkedWriteHandler())

    pipeline.addLast("handler", new HttpHandler(_compilerSrv))
    pipeline
  }
}

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author david.bernard
 * @based on code from org.jboss.netty.example.http.file
 */
@ChannelPipelineCoverage("all")
class HttpHandler(_compilerSrv : CompilerService4Group) extends SimpleChannelUpstreamHandler {
  import java.net.URI
  import scala._

  override
  def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {

    val request = e.getMessage().asInstanceOf[HttpRequest]
    if ((request.getMethod() != HttpMethod.GET) && (request.getMethod() != HttpMethod.POST)) {
      sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, request.getMethod.toString())
      return
    }
    if (request.isChunked()) {
      sendError(ctx, HttpResponseStatus.BAD_REQUEST, "chunck not supported")
      return
    }
    val beginAt = System.currentTimeMillis
    val eventCollector = new EventCollector(Nil)
    val customStatus = action(eventCollector, request)
    new EventLogger(Nil, eventCollector).info("time to process :" + ((System.currentTimeMillis - beginAt).toFloat / 1000) + "s")
    val txt = eventCollector.toCharSequence(true)
    customStatus match {
      case None => sendText(ctx, txt)
      case Some(status) => sendError(ctx, status, txt)
    }
  }

  override
  def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
    val ch = e.getChannel()
    val cause = e.getCause()
    if (cause .isInstanceOf[TooLongFrameException]) {
      sendError(ctx, HttpResponseStatus.BAD_REQUEST, cause);
      return;
    }

    cause.printStackTrace() //TODO log
    if (ch.isConnected()) {
      sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, cause);
    }
  }

  //TODO add time need to process request
  def action(eventCollector : EventCollector, request : HttpRequest) : Option[HttpResponseStatus] = {
    import org.jboss.netty.handler.codec.http.QueryStringDecoder
    import org.yaml.snakeyaml.{Yaml, Loader}
    import org.yaml.snakeyaml.constructor.Constructor

    val decoded = new QueryStringDecoder(request.getUri)

    decoded.getPath.split('/').toList.filter(_.length != 0) match {
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
        actions.split(Array(' ','+')).foldLeft(None : Option[HttpResponseStatus]){ (cumul, i) =>
          cumul match {
            case Some(_) => cumul
            case None => action(eventCollector, i, decoded.getParameters, request)
          }
        }
      }
    }
  }

  //TODO refactor to in/out eventCollector
  def action(eventCollector : EventCollector, act : String, params : java.util.Map[String, java.util.List[String]], request : HttpRequest) : Option[HttpResponseStatus] = {
    import scala.collection.jcl.Conversions._
    import java.util.regex.Pattern
    def findProject() : Option[Pattern] = {
      params.get("p") match {
        case null => None
        case l : java.util.List[String]  if l.size == 1 && l.get(0) != null && !l.get(0).isEmpty => Some(Pattern.compile(l.get(0)))
        case _ => None
      }
    }

    val actLog = new EventLogger(List(act), eventCollector)
    act match {
      case "stop" => {
        System.exit(0)//TODO make are clean smooth stop
        actLog.warn("stopping") //never reach
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
        val before = _compilerSrv.size
        // mime-type should be text/x-yaml (see http://stackoverflow.com/questions/332129/yaml-mime-type)
        var txt = ""
        val yaml = new Yaml()
        var counter = 0
        val cfgAsYamlStr = request.getContent.toString("UTF-8")
        //println(cfgAsYamlStr)
        val it = yaml.loadAll(cfgAsYamlStr).iterator
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
        Some(HttpResponseStatus.BAD_REQUEST)
      }
    }
  }

  private
  def sendText(ctx : ChannelHandlerContext, txt : CharSequence) {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")
    //response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, txt.length)
    response.setContent(ChannelBuffers.copiedBuffer(txt.toString() + "\r\n", "UTF-8"))
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE)
  }

  private
  def sendError(ctx : ChannelHandlerContext, status : HttpResponseStatus, exc : Throwable) {
    val writer = new java.io.StringWriter()
    exc.printStackTrace(new java.io.PrintWriter(writer))
    writer.close()
    sendError(ctx, status, writer.toString)
  }

  private
  def sendError(ctx : ChannelHandlerContext, status : HttpResponseStatus, txt : CharSequence) {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\n" + txt.toString, "UTF-8"));
    // Close the connection as soon as the error message is sent.
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE)
  }
}


