# Hello flat
```scala
import flat._

object Hello extends App with FlatApp {
  app.get("/") { request =>
    Ok("hello!")
  }

  app.start(9000)
}
```

See the [examples](/examples) for a bigger taste- at some point there will actually be docs! Maybe...

# What is flat?
Simply put, flat is an async-first, functional-first, observer pattern based HTTP microframework for Scala with a clean and expressive API. The beating heart of flat is powered by [monix](https://monix.io/), so conceptually everything revolves around an observable stream of requests which are consumed into responses.

# Should I use flat?
For fun, absolutely, but flat is currently experimental. The whole API could change at any moment, or I could find a shiny new side project and stop working on it. If it ever reaches 1.0 I'll revise this, but for now I'd recommend against using it for anything important.

