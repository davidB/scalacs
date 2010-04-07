ScalaCS
=======

The main goals of ScalaCS are :

* to provide a resident compiler server for several projects (=> decrease time need to re-compile from command line tool)
* to ease integration into tools (text editor, IDE)
  * by using an http interface to access services provided by the server (ease integration, see sample clients (sh and java))
  * by providing formatted ouput log (2 regexp to parse)

Why name it ScalaCS ?
ScalaCS is short of scala-compiler-server (but I hope to support other tool in the future (an not only compiler) like code analyzer,...)

![Creative Commons License](http://i.creativecommons.org/l/LGPL/2.1/88x62.png) : This software is licensed under the [CC-GNU LGPL](http://creativecommons.org/licenses/LGPL/2.1/) version 2.1 or later.
(usable in open-source and commercial product)

Installation
------------

Copy the latest jar (scalacs-X.Y-withDeps) from :

* [github](http://github.com/davidB/scalacs/downloads)
* [oss.sonatype.org](http://oss.sonatype.org/content/groups/github/net/alchim31/scalacs).

Server startup
--------------

>  java -jar scalacs-0.2-withDeps_sc2.7.7.jar

Or use scalacs-0.2.jar + dependencies (listed into pom.xml) from the maven central repository.

HTTP Interface
--------------

Some basic client samples are provided

* java class : [BasicHttpScalacsClient.java](/davidB/scalacs/tree/master/src/test/java/net_alchim31_scalacs_client/BasicHttpScalacsClient.java)
* shell script : [scalacs.sh](/davidB/scalacs/tree/master/src/usage/scalacs.sh) (use [cUrl](http://curl.haxx.se/) to do http request and tr to restore multiline message)
* emacs : [scalacs.sh](/davidB/scalacs/tree/master/src/usage/scala-cs.el) (need [cUrl](http://curl.haxx.se/) - Thanks to Minh Do Boi

But best is to call http directly from the editor/IDE you used.

### output format

The output of command that return with HTTP status OK (200) should follow the format readable with regexp :

  `^-(INFO|WARN|ERROR)\t([^\t]*)\t([^\t]*)\t(.*)$`

* group 1 : Level of the message
* group 2 : category of the message
* group 3 : source localisation if not empty use the following regexp to parse :
  `([^#]*)#(\d+),(\d+),(\d+),(\d+)`
  * group 3.1 : absolute path of the file
  * group 3.2 : start line
  * group 3.3 : start column
  * group 3.4 : start charactere offset in the file (some editor/IDE prefer offset to line/column)
  * group 3.5 : length in character
* group 4 : the message with '\n' replaced by '§' (there is no '\r' into the message), so replace '§' by your line feef to have the message on several lines ('\t' are allowed).

You could see the method parse of [BasicHttpScalacsClient.java](/davidB/scalacs/tree/master/src/test/java/net_alchim31_scalacs_client/BasicHttpScalacsClient.java) as sample.

### help, usage

> HTTP GET to : http://127.0.0.1:27616/

Note : multi line message use the character *§* in place of *\n* so editor could grab the full message in on-line (regexp)

### createOrUpdate

Request to createOrUpdate one or more project define in the Yaml syntax, each project definition should be separated by "---"
Project definition should by send as content of
> HTTP POST to : http://127.0.0.1:27616/createOrUpdate

Fields :

* name : name of the project, used as a key
* sourceDirs : list of directory with source files to compile
* includes : filter to select file to compile into sourceDirs(optional)
* excludes : filter to select file to not compile into sourceDirs(optional)
* targetDir : place where to put generated .class
* classpath : list of path (directory/jar) needed to compile files from sourceDirs
* exported : the path of the jar/directory used by other project to reference the current project (optional)
* args : list of additional args to pass to the scalac compiler, could be ignored (depends of the backend)! (optional)

Sample :
<pre><code>
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
</code></pre>

### list-names

List names of registered projects
> HTTP GET to : http://127.0.0.1:27616/list-names

### compile

Request to compile modified projects.
> HTTP GET to : http://127.0.0.1:27616/compile

Request to compile a set of modified projects (with name match pattern)
> HTTP GET to : http://127.0.0.1:27616/compile?p=pattern

### cleanCompiler

Request to clean compiler (cache).
> HTTP GET to : http://127.0.0.1:27616/cleanCompiler

### remove

De-register a set of project (with name match pattern)
> HTTP GET to : http://127.0.0.1:27616/remove?p=pattern

### stop

Stop the server
> HTTP GET to : http://127.0.0.1:27616/stop

Notes
-----

Use 2 separated projects for 'main' and 'test' part, where test has got main into its classpath.

TODO
----

* deploy scalacs into central repository
* integrate sbt, ConditionalCompilation (to avoid recompile all) (need to have sbt into Central repository with correct pom.xml)
* integrate xsbt launcher as bootstrap (need to have xsbti launcher-interface into Central repository with correct pom.xml) [DONE]
* provide support/version for several scala's version
* integrate scalacs with maven-scala-plugin [WIP]
* integrate scalacs with YaScalaDT
* streaming ouput, instead of waiting the end of computation

Thanks
------

to read me to end,
feedbacks are welcome !

/davidB