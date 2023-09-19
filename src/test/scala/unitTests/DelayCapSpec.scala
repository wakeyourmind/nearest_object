package unitTests

import clickhouse.retry.{BackOffMode, DelayCap}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class DelayCapSpec extends AnyFlatSpec with Matchers {
  "capDelay" should "return the original duration when it's less than the max delay" in {
    val originalDelay = 5.seconds
    val cappedDelay   = DelayCap.capDelay(originalDelay, BackOffMode.FixedBackoff)
    cappedDelay shouldEqual originalDelay
  }

  it should "cap the delay to the maximum delay when it's greater" in {
    val originalDelay = 70.seconds
    val cappedDelay   = DelayCap.capDelay(originalDelay, BackOffMode.FixedBackoff)
    cappedDelay shouldEqual 1.seconds
  }

  it should "return the maximum delay when the input is exactly the maximum delay" in {
    val originalDelay = 60.seconds
    val cappedDelay   = DelayCap.capDelay(originalDelay, BackOffMode.FixedBackoff)
    cappedDelay shouldEqual 60.seconds
  }

  it should "return 0 milliseconds when the input is 0 milliseconds" in {
    val originalDelay = 0.millis
    val cappedDelay   = DelayCap.capDelay(originalDelay, BackOffMode.FixedBackoff)
    cappedDelay shouldEqual 0.millis
  }

  it should "return 40 seconds when the input is 20 seconds" in {
    val originalDelay = 20.seconds
    val cappedDelay   = DelayCap.capDelay(originalDelay, BackOffMode.ExponentialBackoff)
    cappedDelay shouldEqual 40.seconds
  }
}
