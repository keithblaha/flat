// Copyright 2016 flat authors

package flat.examples.hello

import flat._
import monix.eval.Task

object Hello extends App {
  app.get("/", request => Task.now {
    OK("hello!")
  })

  app.route("/can-you-handle-anything", request => Task.now {
    OK("you bet")
  })

  app.route("/flat-is-neat", List(GET, POST), request => Task.now {
    OK("i know ;)")
  })

  app.start
}

