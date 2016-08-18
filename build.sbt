organization := "flat"
version := "0.1.0"
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "io.monix" %% "monix" % "2.0-RC10",
  "org.apache.httpcomponents" % "httpcore" % "4.4.5"
)

