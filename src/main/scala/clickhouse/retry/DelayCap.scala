package clickhouse.retry

import scala.concurrent.duration.FiniteDuration

object DelayCap {
  private val maxDelay: Long          = 60000
  private val defaultResetDelay: Long = 1000

  def capDelay(delay: FiniteDuration, backOffMode: BackOffMode): FiniteDuration = {
    if (delay.toMillis > maxDelay) FiniteDuration(defaultResetDelay, scala.concurrent.duration.MILLISECONDS)
    else if (backOffMode == BackOffMode.ExponentialMode) delay * 2
    else delay
  }
}
