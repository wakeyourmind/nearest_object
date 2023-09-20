package clickhouse.retry

import cats.effect.{Concurrent, Sync, Timer}
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxApply, toFlatMapOps}
import clickhouse.retry.RetryException.{RetryBackoffException, RetryLimitedException}
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.{DecodingFailure, ParsingFailure}

import scala.concurrent.duration.FiniteDuration

trait Retry[F[_]] {
  def retryRequest[A](request: F[A]): F[A]
}

object Retry {
  def apply[F[_]: Sync](retryMode: RetryMode)(implicit
      T: Timer[F],
      C: Concurrent[F]
  ): Retry[F] =
    new Retry[F] {
      val log: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      def retryRequest[A](request: F[A]): F[A] = {
        retryMode match {
          case RetryMode.Simple(maxRetries, delay) =>
            retrySimple(request, maxRetries, delay, 0)
          case RetryMode.BackOff(backOffMode, delay) =>
            retryBackOff(request, backOffMode, delay, 0)

        }
      }

      def retrySimple[A](request: F[A], maxRetries: Int, delay: FiniteDuration, retries: Int): F[A] = {
        request.attempt.flatMap {
          case Left(error) if error.isInstanceOf[ParsingFailure] || error.isInstanceOf[DecodingFailure] =>
            Sync[F].raiseError(error)
          case Left(error) if retries < maxRetries =>
            log.error(RetryLimitedException(error, retries, maxRetries).toString) *>
              (T.sleep(delay) *> retrySimple(request, maxRetries, delay, retries + 1))
          case Left(error) =>
            Sync[F].raiseError(RetryException.RetryLimitedException(error, retries, maxRetries))
          case Right(response) =>
            C.pure(response)
        }
      }

      def retryBackOff[A](
          request: F[A],
          backOffMode: BackOffMode,
          delay: FiniteDuration,
          retries: Int
      ): F[A] = {
        request.attempt.flatMap {
          case Left(error) if error.isInstanceOf[ParsingFailure] || error.isInstanceOf[DecodingFailure] =>
            Sync[F].raiseError(error)
          case Left(error) =>
            log.error(RetryBackoffException(error, retries).toString) *>
              (T.sleep(DelayCap.capDelay(delay, backOffMode)) *> retryBackOff(
                request,
                backOffMode,
                DelayCap.capDelay(delay, backOffMode),
                retries + 1
              ))
          case Right(response) =>
            C.pure(response)
        }
      }
    }
}
