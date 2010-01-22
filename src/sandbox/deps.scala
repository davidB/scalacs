
// abstract class Projects {

//   /**
//    * Update the definition of existing project with the same name
//    * Update projects dependency graph
//    */
//   def add(v : ProjectRawInfo)

//   /**
//    * visit the list of project sorted by dependency
//    */
//   def visit(f : ProjectRawInfo => Unit)
// }

// class ProjectRawInfo (
//   val name : String,
//   val sourceDirs : List[File],
//   val sourceIncludes : List[Pattern],
//   val sourceExcludes : List[Pattern],
//   val targetDir : File,
//   val classpath : List[File],
//   val additionalArgs : List[String]
// )


sealed trait UpdateInfo
case class NoUpdate(timestamp : Long) extends UpdateInfo
case class Update(timestamp : Long, logs : String) extends UpdateInfo

trait Updatable {
  // to override by impl
  protected
  def externDeps : List[Updatable] = Nil
  // to override by to impl (incoming for update(...))
  protected
  def checkInternUpdates(timestamp : Long) : UpdateInfo

  def update(timestamp : Long, doUpdate : UpdateInfo => UpdateInfo) : UpdateInfo = {
    def merge(u1 : UpdateInfo, u2 : UpdateInfo) : UpdateInfo = {
      u2 match {
        case NoUpdate(ts) => u1
        case Update(ts, u2logs) => {
          val u1logs = u1 match {
            case NoUpdate(ts) => ""
            case Update(ts, logs) => logs
          }
          Update(ts, u1logs + u2logs)
        }
      }
    }

    val externUpdates = externDeps.foldLeft(NoUpdate(timestamp): UpdateInfo){(result, deps) =>
      merge(result, deps.update(timestamp))
    }
    merge(externUpdates, checkInternUpdates(timestamp)) match {
      case r : NoUpdate => r
      case r : Update => doUpdate(r)
    }
  }

}

//----------------------------
case class VFile(f : File, link : Option[File])

class VFileList extends immutable.List[VFile] {
  def realPath : List[File] = map{ vf =>
    vf.link.filter(_.exists).getOrElse(vf.f)
  }

  def realPathStr = realPath.map(f.getCanonicalPath).mkString(File.separatorPath)

  def update(VFile vf) = {
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
