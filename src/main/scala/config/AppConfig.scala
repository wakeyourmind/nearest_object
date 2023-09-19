package config

import cats.effect.Sync
import pureconfig.ConfigSource

final case class AppConfig(kafka: KafkaConfig, clickhouse: ClickHouseConfig)

object AppConfig {
  def load[F[_]: Sync]: F[AppConfig] = Sync[F].delay {
    import ClickHouseConfig.{uriConfigReader, backOffModeMapper}
    import KafkaConfig.offsetResetMapper
    import pureconfig.generic.auto._
    ConfigSource.default.loadOrThrow[AppConfig]
  }
}
