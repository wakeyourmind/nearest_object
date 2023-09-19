package clickhouse

sealed trait ClickHouseHttpError extends Exception

object ClickHouseHttpError {
  final case class RequestFailureException(status: String, respBody: String) extends ClickHouseHttpError {
    override def toString: String = s"Request failed with the status: $status. Response body: $respBody"

    override def getMessage: String = toString
  }

  final case object ResponseBodyNullException extends ClickHouseHttpError {
    override def toString: String = s"Failed due to nullable response body received"

    override def getMessage: String = toString

  }
}
