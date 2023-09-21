package service

import cats.effect.Concurrent.ops.toAllConcurrentOps
import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.implicits._
import clickhouse.retry.Retry
import clickhouse.{ClickHouseHttp, NearestRecordFromClickHouse, WayRecordToClickHouse}
import config.AppConfig
import domain.{QueryInRecord, QueryOutRecord, WayRecord}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.ExecutionContext

sealed trait JobF[F[_]] {
  def run: F[ExitCode]
}

object JobF {
  def apply[F[_]: Sync: ConcurrentEffect: Timer: ContextShift](implicit executionContext: ExecutionContext): JobF[F] =
    new JobF[F] {
      def run: F[ExitCode] = {
        val resource = for {
          log               <- Resource.eval(Slf4jLogger.create[F])
          _                 <- Resource.eval(log.info("Starting App"))
          appConf           <- Resource.eval(AppConfig.load[F])
          httpClientBuilder <- BlazeClientBuilder[F](executionContext).resource

          retry = Retry[F](
            appConf.clickhouse.retry
          )
          clickHouseClient = ClickHouseHttp(httpClientBuilder, retry)(appConf.clickhouse)

          pipeTask <- {
            val writeCh = WayRecordToClickHouse[F](clickHouseClient)
            val readCh  = NearestRecordFromClickHouse[F](clickHouseClient)
            log.info("Starting NearestObject Pipeline") *> Pipeline
              .run[F, Option[WayRecord], Option[QueryInRecord], Option[QueryOutRecord]](
                appConf.kafka,
                appConf.kafka.consumers.way.consumerSettings[F, Option[WayRecord]],
                appConf.kafka.consumers.queryIn.consumerSettings[F, Option[QueryInRecord]],
                appConf.kafka.producer.producerSettings[F, Option[QueryOutRecord]],
                _.fold(Sync[F].unit)(writeCh.write),
                _.traverse(queryInRecord => readCh.getNearestRecord(queryInRecord))
              )
          }.background
          _ <- Resource.eval(
            (pipeTask *> log.info("App is completed")) >> log.info("Pipeline is finished")
          )
        } yield ExitCode.Success

        resource.use(_ => Sync[F].pure(ExitCode.Success))
      }
    }
}
