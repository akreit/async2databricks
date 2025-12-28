package com.async2databricks.database

import cats.effect.*
import com.async2databricks.model.SampleData
import com.typesafe.scalalogging.LazyLogging
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import java.time.LocalDateTime

trait DataRepository[F[_]] {

  /** Stream data from the database
    */
  def streamData(query: String, batchSize: Int): Stream[F, SampleData]
}

object DataRepository extends LazyLogging {

  def apply[F[_]: Async](xa: Transactor[F]): DataRepository[F] =
    new DataRepository[F] {

      /** Implicit reader for SampleData - using tuple destructuring
        */
      implicit val sampleDataRead: Read[SampleData] =
        Read[(Long, String, Double, String, LocalDateTime)].map {
          case (id, name, value, category, createdAt) =>
            SampleData(id, name, value, category, createdAt)
        }

      override def streamData(
          query: String,
          batchSize: Int
      ): Stream[F, SampleData] = {
        logger.info(s"Starting to stream data with query: $query")

        Fragment
          .const(query)
          .query[SampleData]
          .stream
          .transact(xa)
          .chunkN(batchSize)
          .flatMap(chunk => Stream.chunk(chunk))
          .evalTap(_ =>
            Async[F].delay(logger.debug("Fetched record from database"))
          )
      }
    }
}
