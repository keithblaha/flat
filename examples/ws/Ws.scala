// Copyright 2016 flat authors

package flat.examples.ws

import flat._

object Ws extends App with FlatApp {
  app.ws("/") { ws =>
    ws.onConnect {
      // runs before client has probably connected
      // if wanna do initial thing use first heartbeat
    }

    ws.onMessage { (msg: String) =>
      flat.logging.Logger.debug(s"got message of size ${msg.size}")

      ws.send("")
    }
  }

  app.start(9000)
}
