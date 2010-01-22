package net_alchim31_scalacs

import java.net.InetSocketAddress;
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

      // Set up the event pipeline factory.
      bootstrap.setPipelineFactory(new HttpStaticFileServerPipelineFactory())

      // Bind and start to accept incoming connections.
      println("start waiting request at 27616") //TODO log
      bootstrap.bind(new InetSocketAddress(27616))
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

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author david.bernard
 * @based on code from org.jboss.netty.example.http.file
 */
@ChannelPipelineCoverage("all")
class HttpStaticFileServerHandler(_compilerSrv : CompilerService4Group) extends SimpleChannelUpstreamHandler {
  import java.net.URI

  val sampleConfig = new SingleConfig(
    "sample",
    List(new File("/home/dwayne/work/oss/scala-tools/scala-tools-server/src/main/scala")),
    List(RegExpUtil.globToRegexPattern("*.scala")),
    Nil,
    new File("/home/dwayne/work/oss/scala-tools/scala-tools-server/target/classes"),
    List(
      new File("/home/dwayne/.m2/repository/org/scala-lang/scala-library/2.7.5/scala-library-2.7.5.jar"),
      new File("/home/dwayne/.m2/repository/org/scala-lang/scala-compiler/2.7.5/scala-compiler-2.7.5.jar"),
      new File("/home/dwayne/.m2/repository/org/jboss/netty/netty/3.1.0.GA/netty-3.1.0.GA.jar")
    ),
    List(
      "-deprecation"
    ),
    None
  )

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
    action(request) match {
      case Right(txt) => sendText(ctx, txt)
      case Left(status) => sendError(ctx, status, "")
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


  def action(request : HttpRequest) : Either[HttpResponseStatus, CharSequence] = {
    import org.jboss.netty.handler.codec.http.QueryStringDecoder
    import org.yaml.snakeyaml.{Yaml, Loader}
    import org.yaml.snakeyaml.constructor.Constructor

    val decoded = new QueryStringDecoder(request.getUri)
    val contentType = request.getHeader(HttpHeaders.Names.CONTENT_TYPE)

    decoded.getPath match {
      case "/stop" => {
  System.exit(0)//TODO make are clean smooth stop
  Right("stopping")
      }
      case "/ping" => Right("pong") //TODO return the version of the scala compiler (later provide info about tools)
      case "/mem" => {
        val runtime = Runtime.getRuntime()
        val txt = ("total memory = "+ runtime.totalMemory
          + ", max memory = " + runtime.maxMemory
          + ", free memory = " + runtime.freeMemory
        )
        Right(txt)
      }
      case "/add" /*if "text/x-yaml" == contentType*/ => {
        import scala.collection.jcl.Conversions._
        // mime-type should be text/x-yaml (see http://stackoverflow.com/questions/332129/yaml-mime-type)
        var txt = ""
        val yaml = new Yaml()
        var counter = 0
        val cfgAsYamlStr = request.getContent.toString("UTF-8")
        println(cfgAsYamlStr)
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
            toFileOption("exported"),
          )
          _compilerSrv.add(new CompilerService4Single(cfg, Some(_compilerSrv)))
          txt += "added " + data + " :: " + cfg + "\n"
          counter += 1
        }
        txt += "nb compiler added/total : " + counter+ "/" + _compilerSrv.size + " ... " + contentType + "\n"
        Right(txt)
      }
      // case "/add" => {
      //   Right("only compiler definition in 'text/x-yaml' are supported, your mime-type : " + contentType)
      // }
      case "/reset" => {
        Right(_compilerSrv.reset())
      }
      case "/clean" => {
        Right(_compilerSrv.clean())
      }
      case "/compile" => {
        Right(_compilerSrv.compile(System.currentTimeMillis, true).map(_.out).mkString("\n"))
      }
      case "/clean-compile" => {
        Right(_compilerSrv.clean() + "\n" + _compilerSrv.compile(System.currentTimeMillis, true).map(_.out).mkString("\n"))
      }
      case "/" => {
        Right("""usage :
          /mem : display memory information
          /add : add project (send definition as yaml in content of the request)
                 ex : 'curl http://127.0.0.1:27616/add --data-binary @sample.yaml'
          /reset : reset compilers
          /clean
          /compile
          /clean-compile
        """)
      }
      case unknown => {
        println("unsupported operation : " + unknown)
        Left(HttpResponseStatus.BAD_REQUEST)
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

import org.jboss.netty.channel.ChannelPipelineFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author david.bernard
 * @based on code from org.jboss.netty.example.http.file
 */
class HttpStaticFileServerPipelineFactory extends ChannelPipelineFactory {

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

    pipeline.addLast("handler", new HttpStaticFileServerHandler(_compilerSrv))
    pipeline
  }
}
