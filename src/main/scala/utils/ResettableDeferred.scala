package utils

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._

sealed trait ResettableDeferred[F[_], A] {
  def get: F[A]
  def complete(a: A): F[Unit]
  def reset: F[Unit]
}

object ResettableDeferred {
  def apply[F[_]: Concurrent, A]: F[ResettableDeferred[F, A]] = Concurrent[F].delay {
    new ResettableDeferred[F, A] {
      lazy val initialRef: Ref[F, Deferred[F, A]] = Ref.unsafe(Deferred.unsafe[F, A])

      def get: F[A] = initialRef.get.flatMap(_.get)

      def complete(a: A): F[Unit] = initialRef.get.flatMap(_.complete(a))

      def reset: F[Unit] = {
        for {
          newDeferred <- Concurrent[F].delay(Deferred.unsafe[F, A])
          _           <- initialRef.set(newDeferred)
        } yield ()
      }
    }
  }
}
