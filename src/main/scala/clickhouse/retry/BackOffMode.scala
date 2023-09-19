package clickhouse.retry

sealed abstract class BackOffMode
object BackOffMode {
  private[retry] case object ExponentialMode extends BackOffMode {
    override def toString: String = "Exponential"
  }

  private[retry] case object FixedMode extends BackOffMode {
    override def toString: String = "Fixed"
  }

  val ExponentialBackoff: BackOffMode = ExponentialMode

  val FixedBackoff: BackOffMode = FixedMode
}
