# flat
```scala
import flat._

object Hello extends App with FlatApp {
  app.get("/") { request =>
    OK("hello!")
  }
}
```
