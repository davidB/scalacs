The main goals of [ScalaCS][scalacs] are :

* to provide a resident compiler server for several projects (=> decrease time need to re-compile from command line tool)
* to ease integration into tools (text editor, IDE)
    * by using an http interface to access services provided by the server (ease integration, see sample clients (sh and java))
    * by providing formatted ouput log (2 regexp to parse)

[scalacs]: http://github.com/davidB/scalacs