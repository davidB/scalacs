package net_alchim31_scalacs

import java.io.{IOException, PrintWriter}
import scala.tools.nsc.{Settings}
import scala.tools.nsc.util.{Position, FakePos}
import scala.tools.nsc.reporters.{AbstractReporter}

/**
 * This class implements a Reporter that displays messages on a Logger (no interaction available).
 * Provide output to be machine readable for code marker (parsable with regular expression) :
 * "path#line _number(column_begin,column_end)|message\n" severity define by logger formatter
 *
 * @based on scala.tools.nsc.reporters.ConsoleReporter
 */
class CompilerReporter(val settings: Settings, logger : Logger) extends AbstractReporter {


  def this(settings: Settings) = this(settings, Logger.get("compiler"))

  def display(pos: Position, msg: String, severity: Severity) {
    severity.count += 1
    logMessage(severity, formatMessage(pos, msg))
  }

  /** Prints the message. */
  //def printMessage(msg: String) { writer.println(msg) }  // platform-dependent!
  private
  def logMessage(severity : Severity, msg: String) {
    severity match {
      case ERROR   => logger.error(msg)
      case WARNING => logger.warn(msg)
      case INFO    => logger.info(msg)
    }
  }

  /** Prints the message with the given position indication. */
  private
  def formatMessage(posIn: Position, msg: String) = {
    posIn match {
      case null => String.format("||%s\n",msg)
      case _ => {
        val pos = posIn.inUltimateSource(posIn.source.getOrElse(null))
        pos match {
          case FakePos(pmsg) => String.format("%s|%s\n", pmsg, msg)
          case _ => pos.source match {
            case None => String.format("||%s\n", msg)
            case Some(src) => {
              var (linenum, columnBegin, columnEnd) = pos.line match {
                case None => (0, 0, 0)
                case l => pos.column match {
                  case None => (l, 0, 0)
                  case Some(c0) => {
                    val content = pos.lineContent.stripLineEnd
                    val end = List(' ', ';', '.', '"', '(', '{').map(content.indexOf(_)).filter(_ != -1).sort(_ < _).firstOption.getOrElse(c0+1)
                    (l, c0, end)
                  }
                }
              }
              val m1 = String.format("%s#%d(%d,%d)|%s\n", src.file.path, linenum, columnBegin, columnEnd, msg)
              m1+ '\n' + formatSourceLine(line.pose.get)
            }
          }
        }
      }
    }
  }


  /**
   *  @param pos ...
   */
  private
  def formatSourceLine(pos: Position) {
    val buffer = new StringBuilder(pos.column.get)
    buffer.append(pos.lineContent.stripLineEnd)
    buffer.append('\n')
    formatColumnMarker(pos, buffer)
    buffer.toString()
  }

  /** Prints the column marker of the given position.
   *
   *  @param pos ...
   */
  private
  def formatColumnMarker(pos: Position, buffer : StringBuilder) = if (!pos.column.isEmpty) {
    var i = 1
    while (i < pos.column.get) {
      buffer.append(' ')
      i += 1
    }
    if (pos.column.get > 0) buffer.append('^')
  }

  /** Prints the number of errors and warnings if their are non-zero. */
  def printSummary() {
    if (WARNING.count > 0) logMessage(WARNING, WARNING.count + " WARNING found")
    if (  ERROR.count > 0) logMessage(ERROR, ERROR.count + " ERROR found")
  }

  def displayPrompt: Unit = throw new UnsupportedOperationException("displayPrompt N/A")
}
