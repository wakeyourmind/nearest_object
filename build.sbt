import Dependencies.*

ThisBuild / scalaVersion := "2.13.10"

addCommandAlias("build", "; clean; unitTest; scalafmtSbtCheck; scalafmtCheckAll;")
addCommandAlias("unitTest", ";Test/testOnly *unitTests*")
addCommandAlias("integrationTest", ";Test/testOnly *integrationTests*")

addCompilerPlugin("io.tryp" % "splain" % "1.0.2" cross CrossVersion.patch)

lazy val root = (project in file("."))
  .settings(
    name := "nearest_object",
    moduleName := "nearest_object_app",
    description := "Consuming records from 2 Kafka topics(way & query_in) and publishing the nearest one into the query_out Kafka topic." +
      "The nearest object is determined by finding a minimum distance between query_in.x and way.distance",
    organization := "org.local.practicaltask",
    logLevel := Level.Info,
    scalacOptions ++= Seq("-Xlint:_,-byname-implicit", "-Vimplicits", "-Ywarn-macros:after"),
  )
  .settings {
    libraryDependencies ++= Seq(
      kafka,
      monix,
      pureconfig,
      logback,
      slf4j,
      retryer
    ) ++ circe ++ fs2 ++ cats ++ http4s ++ scalaTest
  }
