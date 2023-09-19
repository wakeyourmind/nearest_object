package service

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import config.KafkaConfig
import fs2.kafka._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import utils.ResettableDeferred

object Pipeline {

  def run[F[_]: ConcurrentEffect: Timer: ContextShift, FirstIn, SecondIn, Out](
      conf: KafkaConfig,
      waySettings: ConsumerSettings[F, Unit, FirstIn],
      queryInSettings: ConsumerSettings[F, Unit, SecondIn],
      queryOutSettings: ProducerSettings[F, Unit, Out],
      wayProcessor: FirstIn => F[Unit],
      queryInProcessor: SecondIn => F[Option[Out]]
  ): F[Unit] = Slf4jLogger.create[F].flatMap { implicit log =>
    {
      def processWayRecord(
          committableRecord: CommittableConsumerRecord[F, Unit, FirstIn]
      ): F[CommittableOffset[F]] = {
        val committableOffset = committableRecord.offset
        val consumerRecord    = committableRecord.record
        val offset            = committableOffset.offsetAndMetadata.offset()
        Option(consumerRecord.value).fold {
          log.warn(s"Got null value from Kafka: $offset") >>
            committableOffset.pure[F]
        } { record =>
          {
            wayProcessor(record)
              .redeemWith(
                {
                  case e: io.circe.Error =>
                    log.error(s"Failed to process record: $record, $e") as committableOffset
                  case other => ConcurrentEffect[F].raiseError(other)
                },
                _ =>
                  log.info(
                    s"Record $record is processed correctly (offset: $offset)"
                  ) >> committableOffset
                    .pure[F]
              )
          }
        }
      }

      def processQueryInRecord(
          committableRecord: CommittableConsumerRecord[F, Unit, SecondIn]
      ): F[ProducerRecords[Unit, Out, CommittableOffset[F]]] = {
        val committableOffset = committableRecord.offset
        val consumerRecord    = committableRecord.record
        val offset            = committableOffset.offsetAndMetadata.offset()
        Option(consumerRecord.value).fold {
          log.warn(s"Got null value from Kafka: $offset") >>
            ProducerRecords(Seq.empty[ProducerRecord[Unit, Out]], committableOffset).pure[F]
        } { rec =>
          queryInProcessor(rec)
            .redeemWith(
              {
                case e: io.circe.Error =>
                  log.error(e)(s"Failed to process record: $consumerRecord") as ProducerRecords(
                    Seq.empty[ProducerRecord[Unit, Out]],
                    committableOffset
                  )
                case other => ConcurrentEffect[F].raiseError(other)
              },
              processedRecord => {
                val producerRecord = processedRecord.map { queryOutRecord =>
                  log.info(
                    s"Record $queryOutRecord is processed correctly (offset: $offset). Input: $rec"
                  ) >> ProducerRecords
                    .one(ProducerRecord(conf.producer.topic, (), queryOutRecord), committableOffset)
                    .pure[F]
                }
                producerRecord.getOrElse(
                  log.error(s"Failed to produce record $producerRecord (offset: $offset). Input: $rec") >>
                    ProducerRecords(Seq.empty[ProducerRecord[Unit, Out]], committableOffset)
                      .pure[F]
                )
              }
            )
        }
      }

      for {
        signalDef <- ResettableDeferred[F, Unit]
        signalRef <- Ref.of[F, Boolean](false)

        way <- Concurrent[F].start {
          KafkaConsumer
            .stream(waySettings)
            .evalTap(_.subscribeTo(conf.consumers.way.topic))
            .flatMap(_.partitionedStream)
            .map(_.evalMap { committableRecord =>
              processWayRecord(committableRecord)
                .flatMap { result =>
                  (for {
                    signal <- signalRef.get
                    _      <- signalRef.set(true).whenA(!signal)
                    _      <- signalDef.complete(()).unlessA(signal)
                  } yield result).pure[F]
                }
            })
            .parJoinUnbounded
            .through(offsetStream => offsetStream.evalMap(record => record.flatMap(_.commit)))
            .compile
            .drain
        }

        queryInAndQueryOut <- Concurrent[F].start {
          KafkaConsumer
            .stream(queryInSettings)
            .evalTap(_.subscribeTo(conf.consumers.queryIn.topic))
            .flatMap(_.partitionedStream)
            .map(_.evalMap { committableRecord =>
              signalDef.get >> signalRef.set(false) >> signalDef.reset >> processQueryInRecord(committableRecord)
            })
            .parJoinUnbounded
            .through(KafkaProducer.pipe(queryOutSettings))
            .map(_.passthrough)
            .through(offsetStream => offsetStream.evalMap(_.commit))
            .compile
            .drain
        }
        _ <- way.join
        _ <- queryInAndQueryOut.join

      } yield ()
    }
  }
}
