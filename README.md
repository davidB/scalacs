ScalaCS
=======

The firsts goals of ScalaCS are :
* to provide a resident compiler server for several projects (into decrease time need to compile from command line tool
* to ease integration into tools (text editor, IDE)
  * by using an http interface to access services provided by the server to ease integration, see sample clients
    * sample shell script, used curl sts.sh (could be adapted easily to Windows, Emacs and any Text editor who allow running external tools)
    * sample java client (to copy/paste, ro adapte) at src/test/java/net_alchim31_scalacs_client/BasicHttpScalacsClient.java
  * by providing formatted ouput log (and regexp to parse +/-)

Why name it ScalaCS ?
ScalaCS is short of scala-compiler-server (but I hope to support other tool in the future (an not only compiler) like code analyzer,...)

![Creative Commons License](http://i.creativecommons.org/l/LGPL/2.1/88x62.png) : This software is licensed under the [CC-GNU LGPL](http://creativecommons.org/licenses/LGPL/2.1/) version 2.1 or later.
(usable in open-source and commercial product)

Installation
------------

copy the latest jar (scalacs-X.Y-withDeps) from scala-tools.org :
* [release](http://scala-tools.org/repo-releases/org/scala-tools/scalacs)
* [snapshot / wip](http://scala-tools.org/repo-snapshots/org/scala-tools/scalacs)

Server startup
--------------
java -jar target/scalacs-0.1-withDeps.jar

HTTP Interface
--------------

h3. help, usage

simply call http://127.0.0.1:27616/

h3. createOrUpdate

Request to createOrUpdate one or more project define in the Yaml syntax, each project definition should be separated by "---"
Project definition should by send as content of the HTTP POST to : http://127.0.0.1:27616/createOrUpdate
Fields :
* name : name of the project, used as a key
* sourceDirs : list of directory with source files to compile
* includes : filter to select file to compile into sourceDirs(optional)
* excludes : filter to select file to not compile into sourceDirs(optional)
* targetDir : place where to put generated .class
* classpath : list of path (directory/jar) need to compile files from sourceDirs
* exported : the path of the jar/directory used by other project to reference the current project (optional)
* args : list of additional args to pass to the scalac compiler, could be ignored (depends of the backend)! (optional)

Sample :
<code>
  name : sample
  sourceDirs :
    - "/home/dwayne/work/oss/scalacs/src/main/scala"
  includes :
    - "*.scala"
  excludes :
  targetDir : "/home/dwayne/work/oss/scalacs/target/classes"
  classpath :
    - "/home/dwayne/.m2/repository/org/scala-lang/scala-library/2.7.5/scala-library-2.7.5.jar"
    - "/home/dwayne/.m2/repository/org/scala-lang/scala-compiler/2.7.5/scala-compiler-2.7.5.jar"
    - "/home/dwayne/.m2/repository/org/jboss/netty/netty/3.1.0.GA/netty-3.1.0.GA.jar"
    - "/home/dwayne/.m2/repository/SnakeYAML/SnakeYAML/1.3/SnakeYAML-1.3.jar"
  exported : ""
  args :
    - "-deprecation"
</code>


h3. compile

Request to compile modified project.
HTTP GET to : http://127.0.0.1:27616/compile

h3. cleanCompiler

Request to clean compiler (cache).
HTTP GET to : http://127.0.0.1:27616/cleanCompiler

TODO
----

* integrate sbt, ConditionalCompilation (to avoid recompile all)
* integrate xsbt launcher as bootstrap
* provide support/version for several scala's version
* do automatic cleanCompiler when dependency jar are modified
* document output/log
* integrate sts into maven-scala-plugin and YaScalaDT
* add a plugin/extension engine (may be based on Guice, osgi)