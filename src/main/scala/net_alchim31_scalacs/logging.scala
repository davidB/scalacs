package net_alchim31_scalacs

import java.io.File

case class SrcLocation(file : File, linenum : Int, columnBegin : Int, offset : Int, length : Int)

abstract sealed class LogLevel
object LogLevel {
  case object INFO extends LogLevel
  case object WARN extends LogLevel
  case object ERROR extends LogLevel
}

trait Logger {
  def category : List[CharSequence]

  def info(msg : CharSequence) = log(LogLevel.INFO, msg, None)
  def warn(msg : CharSequence) = log(LogLevel.WARN, msg, None)
  def error(msg : CharSequence) = log(LogLevel.ERROR, msg, None)
  def error(msg : CharSequence, t : Throwable) {
    error(msg + " [" + t.getClass + " : " + t.getMessage + "]")
    //t.printStackTrace()
  }

  def log(level : LogLevel, msg : CharSequence, loc : Option[SrcLocation]) : Unit
  def newChild(subCategory : CharSequence) : Logger
}

case class Event(category : List[CharSequence], level : LogLevel, msg : CharSequence, loc : Option[SrcLocation])

class EventCollector(initial : List[Event]) {
  private
  var _events : List[Event] = initial

  def this() = this(Nil)

  def add(event : Event) = { _events = event :: _events }
  def addAll(ec : EventCollector) = { _events = ec._events ++ _events}
  def events = _events
  def +(ec : EventCollector) = new EventCollector(ec._events ++ _events)
  def exists(level : LogLevel) = _events.exists(_.level == level)

  //TODO reverse to be in sorted by older->newer
  def toCharSequence(reverseOrder : Boolean) = {
    val str = new StringBuilder()
    val l = reverseOrder match {
      case true => events.reverse
      case false => events
    }
    l.foreach{ event =>
      str.append('-').
        append(event.level).
        append('\t').
        append(event.category.reverse.mkString(".")).
        append('\t')
      event.loc match {
        case Some(loc) => str.append(loc.file).append('#').append(loc.linenum).append(',').append(loc.columnBegin).append(',').append(loc.offset).append(',').append(loc.length)
        case None =>
      }
      str.append('\t').
        append(event.msg.toString.replace('\n', '§').replace('\r', '')).
        append('\n')
    }
    //TODO add  summary (nb error, nb warning)
    str
  }
}

class EventLogger(val category : List[CharSequence], val collector : EventCollector) extends Logger {

  final
  def log(level : LogLevel, msg : CharSequence, loc : Option[SrcLocation]) = collector.add(Event(category, level, msg, loc))

  def newChild(subCategory : CharSequence) : Logger = new EventLogger(subCategory :: category, collector)
}

