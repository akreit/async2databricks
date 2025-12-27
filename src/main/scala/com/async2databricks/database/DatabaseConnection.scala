package com.async2databricks.database

import cats.effect._
import doobie._
import doobie.implicits._
import doobie.hikari.HikariTransactor
import com.async2databricks.config.DatabaseConfig
import com.typesafe.scalalogging.LazyLogging

object DatabaseConnection extends LazyLogging {

  /**
   * Creates a Hikari connection pool transactor
   */
  def createTransactor[F[_]: Async](
    config: DatabaseConfig
  ): Resource[F, HikariTransactor[F]] = {
    for {
      _ <- Resource.eval(Async[F].delay(logger.info(s"Connecting to database: ${config.url}")))
      xa <- HikariTransactor.newHikariTransactor[F](
        config.driver,
        config.url,
        config.user,
        config.password,
        scala.concurrent.ExecutionContext.global
      )
      _ <- Resource.eval(Async[F].delay {
        xa.configure { ds =>
          Async[F].delay {
            ds.setMaximumPoolSize(config.poolSize)
            ds.setConnectionTimeout(30000)
            logger.info(s"Database connection pool configured with size: ${config.poolSize}")
          }
        }
      }.flatten)
    } yield xa
  }
}
