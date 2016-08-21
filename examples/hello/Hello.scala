// Copyright 2016 flat authors

package flat.examples.hello

import flat._

object Hello extends App with FlatApp {
  app.get("/") { request =>
    OK("hello!")
  }

  app.route("/can-you-handle-anything") { request =>
    OK("you bet")
  }

  app.route("/flat-is-neat", List(GET, POST)) { request =>
    OK("i know ;)")
  }

  app.route("/flat-is-lame", List(GET, POST)) { request =>
    BadRequest("""¯\_(ツ)_/¯""")
  }

  app.start
}

