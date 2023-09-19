package clickhouse

import cats.effect.Sync
import domain.WayRecord

sealed trait WayRecordToClickHouse[F[_]] {
  def write(record: WayRecord): F[Unit]
}

object WayRecordToClickHouse {
  def apply[F[_]: Sync](clickHouseHttp: ClickHouseHttp[F]): WayRecordToClickHouse[F] = new WayRecordToClickHouse[F] {
    def write(record: WayRecord): F[Unit] = {
      clickHouseHttp.writeRequest(
        s"INSERT INTO way_record (distance, object) VALUES (${record.distance}, '${record.`object`}')",
        "default"
      )
    }
  }
}
