package unitTests

import cats.effect.{ContextShift, Timer}
import clickhouse.retry.{BackOffMode, Retry, RetryMode}
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt

class RetrySpec extends AnyWordSpec with Matchers {

  implicit val contextShift: ContextShift[Task] = Task.contextShift
  implicit val timer: Timer[Task]               = Task.timer
  implicit val monixScheduler: Scheduler        = Scheduler.global

  "Retry" should {

    "work with a successfully request when it succeeds on the first attempt" in {
      val initialAttempt = Task(21)

      val retry = Retry[Task](RetryMode.Simple(1, 1.seconds))

      val result = retry.retryRequest(initialAttempt).runSyncUnsafe(3.seconds)

      result shouldEqual 21
    }

    "work with a successfully request when it fails and then succeeds" in {
      var attempts = 0
      val initialAttempt = Task {
        attempts += 1
        if (attempts == 3) 42 else throw new RuntimeException("dummy failure")
      }

      val retry = Retry[Task](RetryMode.BackOff(BackOffMode.FixedBackoff, 1.seconds))

      val result = retry.retryRequest(initialAttempt).runSyncUnsafe(5.seconds)

      result shouldEqual 42
      attempts shouldEqual 3
    }

    "fail after exhausting all retries" in {
      val initialAttempt = Task(new Throwable("dummy failure"))

      val retry = Retry[Task](RetryMode.Simple(1, 1.seconds))

      val result = retry.retryRequest(initialAttempt).runSyncUnsafe(3.seconds)

      result.getMessage shouldEqual initialAttempt.runSyncUnsafe(1.seconds).getMessage
    }
  }
}
