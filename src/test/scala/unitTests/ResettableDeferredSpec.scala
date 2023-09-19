package unitTests

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, Timer}
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.ResettableDeferred

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

class ResettableDeferredSpec extends AsyncFlatSpec with Matchers {

  implicit val contextShift: ContextShift[Task] = Task.contextShift
  implicit val timer: Timer[Task]               = Task.timer
  implicit val monixScheduler: Scheduler        = Scheduler.global

  "ResettableDeferred" should "be able to get and complete value" in {
    val expectedVal = 17

    val mockApp = for {
      resettableDef <- ResettableDeferred[Task, Int]
      _             <- resettableDef.complete(expectedVal)
      result        <- resettableDef.get
    } yield result

    mockApp.runToFuture.map { result =>
      result shouldEqual expectedVal
    }

  }

  it should "reset to the initial state" in {
    val initVal = 42

    val mockApp =
      for {
        rd <- ResettableDeferred[Task, Int]
        _  <- rd.complete(initVal)
        _  <- rd.reset
        _  <- rd.get
      } yield ()

    val result = intercept[TimeoutException] {
      mockApp.runSyncUnsafe(3.seconds)
    }

    result.toString should startWith("java.util.concurrent.TimeoutException")
  }

  it should "be concurrent-safe" in {
    val initVal    = 1
    val updatedVal = 21

    val mockApp = for {
      resetDef  <- ResettableDeferred[Task, Int]
      resultRef <- Ref.of[Task, Int](initVal)
      t1 = for {
        _ <- resetDef.complete(updatedVal)
      } yield ()

      t2 = for {
        _ <- Task.sleep(2.seconds)
        _ <- resetDef.get
        _ <- resetDef.reset
        _ <- resultRef.set(updatedVal)
      } yield ()

      _ <- Task.parZip2(t1, t2)

      _ <- resetDef.complete(updatedVal)

      resultOfDef <- resetDef.get
      resultOfRef <- resultRef.get
    } yield (resultOfDef, resultOfRef)

    mockApp.runToFuture.map { case (refResult, defResult) =>
      refResult shouldEqual defResult
    }
  }
}
