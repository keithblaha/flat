// Copyright 2016 flat authors

package flat.logging

import org.slf4j.LoggerFactory

object Logger {
  val logger = LoggerFactory.getLogger("flat")

  def trace(s: String) = logger.trace(s)
  def debug(s: String) = logger.debug(s)
  def info(s: String) = logger.info(s)
  def warn(s: String) = logger.warn(s)
  def error(s: String) = logger.error(s)
  def error(s: String, t: Throwable) = logger.error(s, t)
}

