package domain

import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId, none, toFunctorOps}
import fs2.kafka.{Deserializer, Serializer}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

import java.nio.charset.StandardCharsets

final case class WayRecord(distance: Double, `object`: String)

object WayRecord {
  implicit val decoder: Decoder[WayRecord] = deriveDecoder[WayRecord]

  implicit def deserializer[F[_]: Sync]: Deserializer[F, Option[WayRecord]] = {
    val log = Slf4jLogger.getLogger[F]
    Deserializer.lift { bts =>
      val str = new String(bts, StandardCharsets.UTF_8)
      io.circe.parser.parse(str).flatMap(_.as[WayRecord]) match {
        case Left(value) =>
          log.error(s"Failed to parse: $value. Source: $str").as(none[WayRecord])
        case Right(value) =>
          value.some.pure[F]
      }
    }
  }
}

final case class QueryInRecord(value: Double) extends AnyVal

object QueryInRecord {
  implicit val decoder: Decoder[QueryInRecord] = deriveDecoder[QueryInRecord]

  implicit def deserializer[F[_]: Sync]: Deserializer[F, Option[QueryInRecord]] = {
    val log = Slf4jLogger.getLogger[F]
    Deserializer.lift { bts =>
      val str = new String(bts, StandardCharsets.UTF_8)
      io.circe.parser.parse(str).flatMap(_.as[QueryInRecord]) match {
        case Left(value) =>
          log.error(s"Failed to parse: $value. Source: $str").as(none[QueryInRecord])
        case Right(value) =>
          value.some.pure[F]
      }
    }
  }
}

final case class QueryOutRecord(`object`: String)
object QueryOutRecord {

  implicit val decoder: Decoder[QueryOutRecord] = deriveDecoder[QueryOutRecord]
  implicit val encoder: Encoder[QueryOutRecord] = deriveEncoder[QueryOutRecord]

  implicit def serializer[F[_]: Sync]: Serializer[F, Option[QueryOutRecord]] = {
    val log = Slf4jLogger.getLogger[F]
    Serializer.lift {
      case Some(value) =>
        value.asJson.noSpaces.getBytes(StandardCharsets.UTF_8).pure[F]
      case rec @ _ =>
        log.error(s"Failed to serialize the record: $rec").as(Array.empty[Byte])
    }
  }
}
