package unitTests

import cats.effect.{ContextShift, Resource, Timer}
import clickhouse.ClickHouseHttp
import clickhouse.retry.RetryException.RetryLimitedException
import clickhouse.retry.{Retry, RetryMode}
import config.ClickHouseConfig
import config.ClickHouseConfig.Credentials
import domain.{QueryInRecord, QueryOutRecord}
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.syntax.EncoderOps
import io.circe.{DecodingFailure, ParsingFailure}
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s._
import org.http4s.client.Client
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.DurationInt

class ClickHouseSpec extends AnyWordSpec with Matchers with MockitoSugar {

  implicit val contextShift: ContextShift[Task] = Task.contextShift
  implicit val timer: Timer[Task]               = Task.timer
  implicit val monixScheduler: Scheduler        = Scheduler.global

  private val clickHouseConfig: ClickHouseConfig =
    ClickHouseConfig(
      Uri.unsafeFromString("http://0.0.0.0:8121"),
      Credentials("default", ""),
      RetryMode.Simple(3, 1.seconds)
    )
  private val mockHttpClient: Client[Task] = mock[Client[Task]]
  private val mockRetry: Retry[Task]       = Retry[Task](RetryMode.Simple(1, 1.seconds))

  private val clickHouseHttp: ClickHouseHttp[Task] = ClickHouseHttp(mockHttpClient, mockRetry)(clickHouseConfig)
  private val query                                = "SELECT object FROM table LIMIT 1 FORMAT JSONEachRow"

  val log: SelfAwareStructuredLogger[Task] = Slf4jLogger.getLogger[Task]

  "ClickHouseHttp" should {
    "successfully read request" in {

      val responseBody                         = QueryOutRecord("doodle").asJson.noSpaces
      val response                             = Response[Task](Status.Ok).withEntity(responseBody)
      val resp: Resource[Task, Response[Task]] = Resource.eval(Task.pure(response))

      when(mockHttpClient.run(any[Request[Task]])).thenReturn(resp)

      val result = clickHouseHttp
        .readRequest[QueryOutRecord](query, "")
        .runSyncUnsafe(3.seconds)

      result shouldBe QueryOutRecord("doodle")
    }
    "successfully write request" in {

      val responseBody = "Success"
      val response     = Response[Task](Status.Ok).withEntity(responseBody)

      when(mockHttpClient.run(any[Request[Task]])).thenReturn(Resource.eval(Task.pure(response)))

      noException should be thrownBy {
        clickHouseHttp
          .writeRequest(query, "")
          .runSyncUnsafe(3.seconds)
      }
    }
    "handle unsuccessful request with status code as 400" in {
      val responseBody = QueryOutRecord("doodle").asJson.noSpaces
      val response     = Response[Task](Status.BadRequest).withEntity(responseBody)

      when(mockHttpClient.run(any[Request[Task]])).thenReturn(Resource.eval(Task.pure(response)))

      val exception = intercept[RetryLimitedException] {
        clickHouseHttp
          .readRequest[QueryOutRecord](query, "")
          .runSyncUnsafe(3.seconds)
      }
      exception.getMessage() should startWith("Request failed due to exception")

    }
    "handle unsuccessful request with failed response deserialization" in {
      val responseBody = QueryOutRecord("doodle").asJson.noSpaces
      val response     = Response[Task](Status.Ok).withEntity(responseBody)

      when(mockHttpClient.run(any[Request[Task]])).thenReturn(Resource.eval(Task.pure(response)))

      val exception = intercept[DecodingFailure] {
        clickHouseHttp
          .readRequest[QueryInRecord](query, "")
          .runSyncUnsafe(3.seconds)
      }

      exception.getMessage() should startWith("Attempt to decode value on failed cursor")
    }
    "handle unsuccessful request with failed body response parsing" in {
      val responseBody = "dummy_response"
      val response     = Response[Task](Status.Ok).withEntity(responseBody)

      when(mockHttpClient.run(any[Request[Task]])).thenReturn(Resource.eval(Task.pure(response)))

      val exception = intercept[ParsingFailure] {
        clickHouseHttp
          .readRequest[QueryOutRecord](query, "")
          .runSyncUnsafe(3.seconds)
      }

      exception.getMessage() should startWith("expected json value got")
    }

  }
}
