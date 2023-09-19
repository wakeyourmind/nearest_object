package integrationTests

import cats.effect.Sync
import clickhouse.retry.RetryMode
import com.dimafeng.testcontainers.{ClickHouseContainer, KafkaContainer}
import config.ClickHouseConfig.Credentials
import config.{ClickHouseConfig, KafkaConfig}
import fs2.kafka.AutoOffsetReset
import org.apache.kafka.clients.admin.{AdminClient, DeleteTopicsOptions, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Method, Request, Uri}

import java.time.Duration
import java.util.Properties
import scala.concurrent.duration.DurationInt
import scala.io.{BufferedSource, Source}
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object ObjectHelper {
  def loadDataFromJson(filePath: String): Try[List[String]] = {
    Try {
      val source: BufferedSource = Source.fromFile(getClass.getClassLoader.getResource(filePath).getPath)
      try {
        source.getLines().toList
      } finally {
        source.close()
      }
    }
  }

  def loadTestData(filePath: String): List[String] = {
    loadDataFromJson(filePath) match {
      case Success(data) => data
      case Failure(exception) =>
        println(s"Failed to load test data: ${exception.getMessage}")
        List.empty[String]
    }
  }

  def createKafkaTopics(topics: Seq[String], bootstrapServers: String): Unit = {
    val adminProps = new java.util.Properties()
    adminProps.put("bootstrap.servers", bootstrapServers)

    Try {
      val adminClient  = AdminClient.create(adminProps)
      val newTopics    = topics.map(topicName => new NewTopic(topicName, 1, 1.toShort)).asJava
      val createResult = adminClient.createTopics(newTopics)
      createResult.all().get()
      println(s"Created Kafka topics: ${topics.mkString(", ")}")
    } match {
      case Success(_) =>
      case Failure(ex) =>
        println(s"Failed to create Kafka topics: ${topics.mkString(", ")}")
        ex.printStackTrace()
    }
  }

  def produceToKafka(topic: String, messages: Seq[String], container: KafkaContainer): Unit = {
    val producerProps = createKafkaProducerProperties(container.bootstrapServers)

    val producer = new KafkaProducer[String, String](producerProps)
    try {
      messages.foreach { message =>
        producer.send(new ProducerRecord(topic, "", message))
      }
    } finally {
      producer.close()
    }
  }

  def createKafkaProducerProperties(bootstrapServers: String): Properties = {
    val kafkaProducerConfig: Map[String, AnyRef] = Map(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG      -> bootstrapServers,
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG   -> "org.apache.kafka.common.serialization.StringSerializer",
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringSerializer"
    )
    val producerProps = new Properties()
    kafkaProducerConfig.foreach { case (key, value) =>
      producerProps.put(key, value.toString)
    }
    producerProps
  }

  def createKafkaConfig(bootstrapServers: String): KafkaConfig = {
    KafkaConfig(
      consumers = KafkaConfig.Consumers(
        way = KafkaConfig.ConsumerConfig(
          topic = "way",
          bootstrapServers = bootstrapServers,
          autoOffsetReset = AutoOffsetReset.Earliest,
          groupId = "test1"
        ),
        queryIn = KafkaConfig.ConsumerConfig(
          topic = "query_in",
          bootstrapServers = bootstrapServers,
          autoOffsetReset = AutoOffsetReset.Earliest,
          groupId = "test1"
        )
      ),
      producer = KafkaConfig.ProducerConfig(
        topic = "query_out",
        bootstrapServers = bootstrapServers
      )
    )
  }

  def createClickHouseConfig(container: ClickHouseContainer): ClickHouseConfig = {
    val portPattern: Regex = """.*:(\d+)/.*""".r

    val port: Option[String] = container.jdbcUrl match {
      case portPattern(port) => Some(port)
      case _                 => None
    }
    ClickHouseConfig(
      url = Uri.unsafeFromString(
        s"http://${container.containerIpAddress}:${port.getOrElse("8123")}?database=default"
      ),
      credentials = Credentials(container.container.getUsername, container.container.getPassword),
      retry = RetryMode.Simple(5, 1.seconds)
    )
  }

  def createInitTable[F[_]: Sync](
      client: Client[F]
  )(config: ClickHouseConfig): F[Unit] = {
    val request = Request[F](
      Method.POST,
      config.url
        .withQueryParam("database", "default")
        .withQueryParam(
          "query",
          """
        |CREATE TABLE IF NOT EXISTS way_record
        |(
        |    distance Float64,
        |    object String
        |) ENGINE = MergeTree()
        |ORDER BY distance;
        |""".stripMargin
        )
    )
      .withHeaders(
        Authorization(
          BasicCredentials(config.credentials.username, config.credentials.password)
        )
      )

    client.run(request).use { response =>
      if (response.status.isSuccess) {
        Sync[F].delay(println("Table way_record successfully created!"))
      } else {
        Sync[F].delay(println(s"Request failed with status: ${response.status}"))
      }
    }
  }

  def consumeFromKafka(topic: String, bootstrapServers: String, expectedCount: Int): List[String] = {
    val consumerConfig: Map[String, AnyRef] = Map(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG        -> bootstrapServers,
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG        -> "earliest",
      ConsumerConfig.GROUP_ID_CONFIG                 -> "test-consumer-group",
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG   -> "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer"
    )

    val consumerProps = new Properties()
    consumerConfig.foreach { case (key, value) =>
      consumerProps.put(key, value.toString)
    }

    val consumer = new KafkaConsumer[String, String](consumerProps)
    consumer.subscribe(Seq(topic).asJava)

    val consumedRecords = new scala.collection.mutable.ListBuffer[String]()
    while (consumedRecords.size < expectedCount) {
      val records = consumer.poll(Duration.ofMillis(5))
      records.iterator().asScala.foreach { record =>
        val value = record.value()
        consumedRecords += value
      }
    }
    consumer.close()

    consumedRecords.toList
  }

  def dropInitTable[F[_]: Sync](
      client: Client[F]
  )(config: ClickHouseConfig): F[Unit] = {
    val request = Request[F](
      Method.POST,
      config.url
        .withQueryParam("database", "default")
        .withQueryParam(
          "query",
          """
            |DROP TABLE IF EXISTS way_record;
            |""".stripMargin
        )
    )
      .withHeaders(
        Authorization(
          BasicCredentials(config.credentials.username, config.credentials.password)
        )
      )

    client.run(request).use { response =>
      if (response.status.isSuccess) {
        Sync[F].delay(println("Table way_record successfully deleted!"))
      } else {
        Sync[F].delay(println(s"Request failed with status: ${response.status}"))
      }
    }
  }

  def deleteKafkaTopics(topics: Seq[String], bootstrapServers: String): Unit = {
    val adminProps = new java.util.Properties()
    adminProps.put("bootstrap.servers", bootstrapServers)

    Try {
      val admin        = AdminClient.create(adminProps)
      val options      = new DeleteTopicsOptions().timeoutMs(5000)
      val deleteTopics = admin.deleteTopics(topics.asJava, options)
      deleteTopics.all().get()
      println(s"Deleted Kafka topics: ${topics.mkString(", ")}")
    } match {
      case Success(_) =>
      case Failure(ex) =>
        println(s"Failed to delete Kafka topics: ${topics.mkString(", ")}")
        ex.printStackTrace()
    }
  }

}
