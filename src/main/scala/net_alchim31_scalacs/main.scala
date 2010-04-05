package net_alchim31_scalacs

class Main {
  def main(args: Array[String]) {
    HttpServer.main(args)
  }
}

//@see http://code.google.com/p/simple-build-tool/wiki/GeneralizedLauncher
class Main4Launch extends xsbti.AppMain {
  import actors.Exit
  def run(configuration: xsbti.AppConfiguration) = {
//    // get the version of Scala used to launch the application
//    val scalaVersion = configuration.provider.scalaProvider.version
//    // Print a message and the arguments to the application
//    println("Hello world!  Running Scala " + scalaVersion)
//    configuration.arguments.foreach(println)
//    // demonstrate the ability to reboot the application into different versions of Scala
//    // and how to return the code to exit with
//    scalaVersion match
//    {
//      case "2.7.7" =>
//        new xsbti.Reboot {
//          def arguments = configuration.arguments
//          def baseDirectory = configuration.baseDirectory
//          def scalaVersion = "2.7.4"
//          def app = configuration.provider.id
//        }
//      case "2.7.4" => new Exit(1)
//      case _ => new Exit(0)
//    }
    HttpServer.main(configuration.arguments)
    new Exit(0)
  }
  class Exit(val code : Int) extends xsbti.Exit
}