lazy val commonSettings = Seq(
  organization := "flat",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val flat = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    scalaSource in Compile := baseDirectory.value / "src",
    resourceDirectory in Compile := baseDirectory.value / "resources",
    scalaSource in Test := baseDirectory.value / "test" / "src",
    resourceDirectory in Test := baseDirectory.value / "test" / "resources",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "io.circe" %% "circe-core" % "0.4.1",
      "io.circe" %% "circe-generic" % "0.4.1",
      "io.circe" %% "circe-parser" % "0.4.1",
      "io.monix" %% "monix" % "2.0-RC10",
      "org.apache.httpcomponents" % "httpcore" % "4.4.5"
    )
  )

lazy val helloPath = "examples/hello"
lazy val hello = (project in file(helloPath))
  .settings(commonSettings: _*)
  .settings(
    scalaSource in Compile := baseDirectory.value / helloPath
  )
  .dependsOn(flat)

lazy val jsonPath = "examples/json"
lazy val json = (project in file(jsonPath))
  .settings(commonSettings: _*)
  .settings(
    scalaSource in Compile := baseDirectory.value / jsonPath,

    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    )
  )
  .dependsOn(flat)

