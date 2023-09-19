package clickhouse.retry

import scala.concurrent.duration.FiniteDuration

sealed trait RetryMode

object RetryMode {

  final case class BackOff(mode: BackOffMode, delay: FiniteDuration) extends RetryMode
  final case class Simple(maxRetries: Int, delay: FiniteDuration)    extends RetryMode

}
