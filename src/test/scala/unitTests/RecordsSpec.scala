package unitTests

import domain.{QueryInRecord, QueryOutRecord, WayRecord}
import fs2.kafka.{Deserializer, Serializer}
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.nio.charset.StandardCharsets

class RecordsSpec extends AsyncWordSpec with Matchers {
  implicit val monixScheduler: Scheduler                                     = Scheduler.global
  private val wayDeserializer: Deserializer[Task, Option[WayRecord]]         = WayRecord.deserializer[Task]
  private val queryInDeserializer: Deserializer[Task, Option[QueryInRecord]] = QueryInRecord.deserializer[Task]
  private val queryOutSerializer: Serializer[Task, Option[QueryOutRecord]]   = QueryOutRecord.serializer[Task]

  "WayRecord" should {
    "be deserializable" in {
      val bytes  = """{"distance": 10.5, "object": "example"}""".getBytes
      val result = wayDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe Some(WayRecord(10.5, "example"))
      }
    }
    "not be deserializable with incorrect selected keys" in {
      val bytes  = """{"hit": 10.5, "doodoo": "example"}""".getBytes
      val result = wayDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe None
      }
    }
    "not be deserializable with incorrect data type for a distance" in {
      val bytes  = """{"distance": "aga", "object": "example"}""".getBytes
      val result = wayDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe None
      }
    }
  }

  "QueryInRecord" should {
    "be deserializable" in {
      val bytes  = """{"value": 42.0}""".getBytes
      val result = queryInDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe Some(QueryInRecord(42.0))
      }
    }
    "be deserializable with negative value" in {
      val bytes  = """{"value": -0.723}""".getBytes
      val result = queryInDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe Some(QueryInRecord(-0.723))
      }
    }
    "not be deserializable with the incorrect selected key" in {
      val bytes  = """{"doodoo": 42.0}""".getBytes
      val result = queryInDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe None
      }
    }
    "not be deserializable with incorrect data type for a value" in {
      val bytes  = """{"value": "asd""}""".getBytes
      val result = queryInDeserializer.deserialize("", null, bytes)
      result.runToFuture.map { recordOpt =>
        recordOpt shouldBe None
      }
    }
  }

  "QueryOutRecord" should {
    "be serializable" in {
      val record = QueryOutRecord("output")
      val result = queryOutSerializer.serialize("", null, Some(record))
      result.runToFuture.map { bytes =>
        val jsonString = new String(bytes, StandardCharsets.UTF_8)
        jsonString shouldBe """{"object":"output"}"""
      }
    }
    "not be serializable" in {
      val record = QueryOutRecord("output")
      val result = queryOutSerializer.serialize("", null, Some(record))
      result.runToFuture.map { bytes =>
        val jsonString = new String(bytes, StandardCharsets.UTF_8)
        jsonString shouldNot contain("""{"s123":"output"}""")
      }
    }
  }

}
