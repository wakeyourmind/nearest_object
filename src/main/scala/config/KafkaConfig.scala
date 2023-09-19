package config

import cats.effect.Sync
import fs2.kafka._
import pureconfig.ConfigReader

import scala.util.{Failure, Success}

final case class KafkaConfig(
    consumers: KafkaConfig.Consumers,
    producer: KafkaConfig.ProducerConfig
)

object KafkaConfig {

  implicit val offsetResetMapper: ConfigReader[AutoOffsetReset] = ConfigReader.fromStringTry {
    _.toLowerCase match {
      case "earliest" => Success(AutoOffsetReset.Earliest)
      case "latest"   => Success(AutoOffsetReset.Latest)
      case "none"     => Success(AutoOffsetReset.None)
      case x          => Failure(new Exception(s"Failed to read Kafka offset-reset: $x"))
    }
  }

  final case class Consumers(way: ConsumerConfig, queryIn: ConsumerConfig)

  final case class ConsumerConfig(
      topic: String,
      bootstrapServers: String,
      autoOffsetReset: AutoOffsetReset,
      groupId: String,
  ) {
    def consumerSettings[F[_]: Sync, V](implicit deserializer: Deserializer[F, V]): ConsumerSettings[F, Unit, V] =
      ConsumerSettings[F, Unit, V]
        .withBootstrapServers(bootstrapServers)
        .withGroupId(groupId)
        .withAutoOffsetReset(autoOffsetReset)
  }

  final case class ProducerConfig(
      topic: String,
      bootstrapServers: String,
  ) {
    def producerSettings[F[_]: Sync, V](implicit serializer: Serializer[F, V]): ProducerSettings[F, Unit, V] = {
      ProducerSettings[F, Unit, V]
        .withBootstrapServers(bootstrapServers)
    }
  }

}
