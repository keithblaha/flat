// Copyright 2016 flat authors

package flat.examples.json

import flat._
import io.circe.generic.JsonCodec
import io.circe.syntax._

object Json extends App with FlatApp {
  @JsonCodec case class Point(x: Double, y: Double)
  app.get("/point") { request =>
    val point = Point(0, 0)
    Ok(point.asJson)
  }

  @JsonCodec case class Circle(label: String, radius: Double, point: Point)
  app.get("/circle") { request =>
    val circle = Circle("unit", 1, Point(0, 0))
    Ok(circle.asJson)
  }

  @JsonCodec case class Polygon(points: List[Point])
  app.get("/square") { request =>
    val square = Polygon(List(
      Point(1, 1),
      Point(1, -1),
      Point(-1, -1),
      Point(-1, 1)
    ))
    Ok(square.asJson)
  }

  app.start
}

