package com.async2databricks.database

import cats.effect.*
import com.async2databricks.model.SampleData
import com.typesafe.scalalogging.LazyLogging
import doobie.*
import doobie.implicits.*
import fs2.Stream

trait DataRepository[F[_]] {

  /** Stream data from the database
    */
  def streamData(query: String, batchSize: Int): Stream[F, SampleData]
}

object DataRepository extends LazyLogging {

  def apply[F[_]: Async](xa: Transactor[F]): DataRepository[F] =
    (query: String, batchSize: Int) => {
      logger.info(s"Starting to stream data with query: $query")

      Fragment
        .const(query)
        .query[SampleData]
        .stream
        .transact(xa)
        .chunkN(batchSize)
        .flatMap(chunk => Stream.chunk(chunk))
    }
}
