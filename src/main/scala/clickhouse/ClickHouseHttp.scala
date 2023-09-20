package clickhouse

import cats.effect.{Concurrent, Resource, Sync, Timer}
import cats.implicits.{catsSyntaxFlatMapOps, toFlatMapOps}
import clickhouse.ClickHouseHttpError.{RequestFailureException, ResponseBodyNullException}
import clickhouse.retry.Retry
import config.ClickHouseConfig
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.parser.parse
import org.http4s.Status.Successful
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Authorization

trait ClickHouseHttp[F[_]] {
  def readRequest[A](query: String, database: String)(implicit decoder: Decoder[A]): F[A]
  def writeRequest(query: String, database: String): F[Unit]
}

object ClickHouseHttp {
  def apply[F[_]: Sync: Concurrent: Timer](
      httpClient: Client[F],
      retry: Retry[F]
  )(clickHouseConfig: ClickHouseConfig): ClickHouseHttp[F] =
    new ClickHouseHttp[F] {
      val log: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      def readRequest[A](query: String, database: String)(implicit decoder: Decoder[A]): F[A] = {
        val request = Request[F](
          Method.GET,
          clickHouseConfig.url
            .withQueryParam("database", database)
            .withQueryParam("query", query)
        )
          .withHeaders(
            Authorization(
              BasicCredentials(clickHouseConfig.credentials.username, clickHouseConfig.credentials.password)
            )
          )

        retry.retryRequest(parseJsonResponse(httpClient.run(request)))
      }

      def writeRequest(query: String, database: String): F[Unit] = {

        val request = Request[F](
          Method.POST,
          clickHouseConfig.url
            .withQueryParam("database", database)
            .withQueryParam("query", query)
        )
          .withHeaders(
            Authorization(
              BasicCredentials(clickHouseConfig.credentials.username, clickHouseConfig.credentials.password)
            )
          )
        retry.retryRequest(handleResponse(httpClient.run(request)))
      }
      def handleResponse(resourceResp: Resource[F, Response[F]]): F[Unit] = {
        resourceResp.use {
          case Successful(_) => Sync[F].unit
          case _ @resp =>
            resp.as[String].flatMap { responseBody =>
              Sync[F]
                .raiseError[Unit](
                  RequestFailureException(s"${resp.status}", responseBody)
                )
            }
        }
      }

      def parseJsonResponse[A](
          resourceResp: Resource[F, Response[F]]
      )(implicit decoder: Decoder[A]): F[A] = {
        resourceResp.use {
          case Successful(response) =>
            response.as[String].flatMap { responseBody =>
              if (responseBody.trim.isEmpty) {
                Sync[F].raiseError[A](ResponseBodyNullException)
              } else {
                parse(responseBody) match {
                  case Right(json) =>
                    json
                      .as[A]
                      .fold(
                        error =>
                          log.error(error)(error.getMessage()) >> Sync[F]
                            .raiseError[A](error),
                        result => Sync[F].pure(result)
                      )
                  case Left(error) =>
                    log.error(error)(error.getMessage()) >> Sync[F].raiseError[A](error)
                }
              }
            }
          case _ @resp =>
            resp.as[String].flatMap { responseBody =>
              Sync[F]
                .raiseError[A](
                  RequestFailureException(s"${resp.status}", responseBody)
                )
            }
        }
      }
    }

}
