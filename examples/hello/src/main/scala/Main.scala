// Copyright 2016 flat authors

package flat.examples.hello

import flat._
import monix.eval.Task

object Main extends App {
  app.route("/", GET, request => Task.now {
    OK("<html><body><h1>hello!</h1></body></html>")
  })

  app.start
}

