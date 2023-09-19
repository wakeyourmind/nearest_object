package clickhouse

import cats.effect.Sync
import domain.{QueryInRecord, QueryOutRecord}

sealed trait NearestRecordFromClickHouse[F[_]] {
  def getNearestRecord(distance: QueryInRecord): F[Option[QueryOutRecord]]
}

object NearestRecordFromClickHouse {
  def apply[F[_]: Sync](clickHouseHttp: ClickHouseHttp[F]): NearestRecordFromClickHouse[F] =
    new NearestRecordFromClickHouse[F] {
      def getNearestRecord(distance: QueryInRecord): F[Option[QueryOutRecord]] = {
        clickHouseHttp.readRequest(
          s"SELECT object FROM way_record ORDER BY ABS(distance - ${distance.value}) LIMIT 1 FORMAT JSONEachRow",
          "default"
        )
      }
    }
}
