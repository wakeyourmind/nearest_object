package clickhouse.retry

sealed trait RetryException extends Exception

object RetryException {
  final case class RetryBackoffException(cause: Throwable, retries: Int) extends RetryException {
    override def toString: String =
      s"Request failed due to exception on attempt #$retries. Cause: ${cause.getMessage}"

    override def getMessage: String = toString

    override def getCause: Throwable = cause

  }

  final case class RetryLimitedException(cause: Throwable, retries: Int, maxRetries: Int) extends RetryException {
    override def toString: String =
      s"Request failed due to exception on #$retries of #$maxRetries attempts. Cause: ${cause.getMessage}"

    override def getMessage: String = toString

    override def getCause: Throwable = cause
  }
}
