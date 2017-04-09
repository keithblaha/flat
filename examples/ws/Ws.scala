// Copyright 2016 flat authors

package flat.examples.ws

import flat._

object Ws extends App with FlatApp {
  app.ws("/") { ws =>
    ws.onConnect {
      // not guaranteed to run before client has fully connected
    }

    ws.onMessage { (msg: String) =>
      ws.send("hi there")
    }
  }

  app.start(9000)
}
