/*
 * Derived from the work of Lightbend, Inc.
 * Source: https://github.com/playframework/playframework/blob/2.5.4/framework/src/play-logback/src/main/scala/play.api.libs.logback/ColoredLevel.scala
 */

package flat.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class ColoredLevel extends ClassicConverter {
  def convert(event: ILoggingEvent): String = {
    event.getLevel match {
      case Level.TRACE => s"${Console.CYAN}TRACE${Console.RESET}"
      case Level.DEBUG => s"${Console.BLUE}DEBUG${Console.RESET}"
      case Level.INFO  => s"${Console.GREEN}INFO${Console.RESET}"
      case Level.WARN  => s"${Console.YELLOW}WARN${Console.RESET}"
      case Level.ERROR => s"${Console.RED}ERROR${Console.RESET}"
    }
  }
}

