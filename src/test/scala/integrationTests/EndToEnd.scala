package integrationTests

import cats.data.OptionT
import cats.effect.{ContextShift, Timer}
import cats.implicits.toTraverseOps
import clickhouse.retry.{BackOffMode, Retry, RetryMode}
import clickhouse.{ClickHouseHttp, NearestRecordFromClickHouse, WayRecordToClickHouse}
import com.dimafeng.testcontainers.{ClickHouseContainer, ForAllTestContainer, KafkaContainer}
import domain.{QueryInRecord, QueryOutRecord, WayRecord}
import fs2.kafka.{ConsumerSettings, ProducerSettings}
import integrationTests.ObjectHelper._
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import service.Pipeline

import scala.concurrent.duration.DurationInt

class EndToEnd extends AnyFlatSpec with Matchers with ForAllTestContainer with BeforeAndAfterAll {
  override val container: ClickHouseContainer = new ClickHouseContainer(
    DockerImageName.parse("yandex/clickhouse-server")
  )
  private val kafkaContainer: KafkaContainer    = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.0"))
  implicit val contextShift: ContextShift[Task] = Task.contextShift
  implicit val timer: Timer[Task]               = Task.timer
  implicit val monixScheduler: Scheduler        = Scheduler.global

  override def beforeAll(): Unit = {
    kafkaContainer.start()
    container.start()
  }

  override def afterAll(): Unit = {
    kafkaContainer.stop()
    container.stop()
  }

  "Pipeline" should "process all records successfully and equals to expected results" in {

    val wayRecords     = loadTestData("records/wayRecords.json")
    val queryInRecords = loadTestData("records/queryInRecords.json")

    val kafkaConfig = createKafkaConfig(kafkaContainer.bootstrapServers)

    createKafkaTopics(
      Seq(kafkaConfig.consumers.way.topic, kafkaConfig.consumers.queryIn.topic, kafkaConfig.producer.topic),
      kafkaContainer.bootstrapServers
    )

    produceToKafka(kafkaConfig.consumers.way.topic, wayRecords, kafkaContainer)
    produceToKafka(kafkaConfig.consumers.queryIn.topic, queryInRecords, kafkaContainer)

    val clickHouseConfig = createClickHouseConfig(container)

    BlazeClientBuilder[Task](monixScheduler).resource.use { httpClientBuilder =>
      val retry    = Retry[Task](RetryMode.BackOff(BackOffMode.ExponentialBackoff, 1.seconds))
      val chClient = ClickHouseHttp(httpClientBuilder, retry)(clickHouseConfig)

      createInitTable(httpClientBuilder)(clickHouseConfig).runSyncUnsafe(3.seconds)

      val writeCh = WayRecordToClickHouse(chClient)
      val readCh  = NearestRecordFromClickHouse(chClient)

      Pipeline
        .run[Task, Option[WayRecord], Option[QueryInRecord], Option[QueryOutRecord]](
          kafkaConfig,
          ConsumerSettings[Task, Unit, Option[WayRecord]]
            .withBootstrapServers(kafkaConfig.consumers.way.bootstrapServers)
            .withGroupId(kafkaConfig.consumers.way.groupId)
            .withAutoOffsetReset(kafkaConfig.consumers.way.autoOffsetReset),
          ConsumerSettings[Task, Unit, Option[QueryInRecord]]
            .withBootstrapServers(kafkaConfig.consumers.queryIn.bootstrapServers)
            .withGroupId(kafkaConfig.consumers.queryIn.groupId)
            .withAutoOffsetReset(kafkaConfig.consumers.queryIn.autoOffsetReset),
          ProducerSettings[Task, Unit, Option[QueryOutRecord]]
            .withBootstrapServers(kafkaConfig.producer.bootstrapServers),
          _.fold(Task.unit)(wayRecord => writeCh.write(wayRecord)),
          _.traverse(queryInRecord => OptionT(readCh.getNearestRecord(queryInRecord)).value)
        )
    }.runAsyncAndForget

    val consumedRecords =
      consumeFromKafka(kafkaConfig.producer.topic, kafkaConfig.producer.bootstrapServers, queryInRecords.length)

    BlazeClientBuilder[Task](monixScheduler).resource
      .use { httpClientBuilder =>
        dropInitTable(httpClientBuilder)(clickHouseConfig)
      }
      .runSyncUnsafe(3.seconds)

    deleteKafkaTopics(
      Seq(kafkaConfig.consumers.queryIn.topic, kafkaConfig.consumers.way.topic, kafkaConfig.producer.topic),
      kafkaContainer.bootstrapServers
    )

    consumedRecords.nonEmpty shouldBe true
  }

}
