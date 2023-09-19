import sbt.*

object Dependencies {
  val kafka = "org.apache.kafka" %% "kafka" % "2.8.1"
  val monix = "io.monix"         %% "monix" % "3.4.1"
  val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-generic-extras",
    "io.circe" %% "circe-parser",
  ).map(_ % "0.14.1")
  val cats = Seq(
    "org.typelevel" %% "cats-effect" % "2.5.4",
    "org.typelevel" %% "cats-core"   % "2.7.0"
  )
  val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.17.4"
  val fs2 = Seq(
    "co.fs2"          %% "fs2-core"  % "2.5.11",
    "com.github.fd4s" %% "fs2-kafka" % "1.9.0",
  )
  val http4s = Seq(
    "org.http4s" %% "http4s-dsl",
    "org.http4s" %% "http4s-blaze-client"
  ).map(_ % "0.22.8")
  val slf4j   = "io.chrisdavenport" %% "log4cats-slf4j"  % "1.1.1"
  val logback = "ch.qos.logback"     % "logback-classic" % "1.2.11"
  val retryer = "com.github.cb372"  %% "cats-retry"      % "2.1.1"
  val scalaTest =
    Seq(
      "org.scalatest"       %% "scalatest"                       % "3.2.15"   % Test,
      "org.scalatestplus"   %% "mockito-3-4"                     % "3.2.10.0" % Test,
      "com.dimafeng"        %% "testcontainers-scala-scalatest"  % "0.40.12"  % Test,
      "com.dimafeng"        %% "testcontainers-scala-kafka"      % "0.40.12"  % Test,
      "com.dimafeng"        %% "testcontainers-scala-clickhouse" % "0.40.12"  % Test,
      "ru.yandex.clickhouse" % "clickhouse-jdbc"                 % "0.3.2"    % Test
    )
}
