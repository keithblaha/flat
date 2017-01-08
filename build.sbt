lazy val commonSettings = Seq(
  organization := "flat",
  version := "0.6.0",
  scalaVersion := "2.12.1"
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
      "io.circe" %% "circe-core" % "0.6.1",
      "io.circe" %% "circe-generic" % "0.6.1",
      "io.circe" %% "circe-parser" % "0.6.1",
      "io.monix" %% "monix" % "2.1.2",
      "org.apache.httpcomponents" % "httpcore" % "4.4.5",
      "org.apache.httpcomponents" % "httpclient" % "4.5.2",

      "org.scalatest" %% "scalatest" % "3.0.0" % "test"
    ),

    fork in Test := true,
    javaOptions in Test +="-Dlogger.resource=logback-test.xml"
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

