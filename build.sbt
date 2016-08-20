lazy val commonSettings = Seq(
  organization := "flat",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val flat = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "io.monix" %% "monix" % "2.0-RC10",
      "org.apache.httpcomponents" % "httpcore" % "4.4.5"
    )
  )

lazy val hello = (project in file("examples/hello"))
  .settings(commonSettings: _*)
  .dependsOn(flat)

