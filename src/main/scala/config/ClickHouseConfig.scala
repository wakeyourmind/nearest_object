package config

import clickhouse.retry.{BackOffMode, RetryMode}
import org.http4s.Uri
import pureconfig.ConfigReader

import scala.util.{Failure, Success}

final case class ClickHouseConfig(
    url: Uri,
    credentials: ClickHouseConfig.Credentials,
    retry: RetryMode
)

object ClickHouseConfig {
  implicit val uriConfigReader: ConfigReader[Uri] =
    ConfigReader.fromStringTry(s => Uri.fromString(s).toTry)

  implicit val backOffModeMapper: ConfigReader[BackOffMode] = ConfigReader.fromStringTry {
    _.toLowerCase match {
      case "exp" | "exponential" => Success(BackOffMode.ExponentialBackoff)
      case "fixed"               => Success(BackOffMode.FixedBackoff)
      case x                     => Failure(new Exception(s"Failed to read BackOff mode: $x"))
    }
  }

  final case class Credentials(username: String, password: String)

}
