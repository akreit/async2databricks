package com.async2databricks.etl

import cats.effect._
import cats.implicits._
import com.async2databricks.config.AppConfig
import com.async2databricks.database.{DatabaseConnection, DataRepository}
import com.async2databricks.s3.S3Writer
import com.typesafe.scalalogging.LazyLogging

/**
 * Main ETL Pipeline orchestrator
 * Streams data from PostgreSQL and writes to S3 as Parquet
 */
class EtlPipeline[F[_]: Async](config: AppConfig) extends LazyLogging {

  /**
   * Execute the ETL pipeline
   */
  def run(): F[Unit] = {
    logger.info("Starting ETL Pipeline")
    
    val resources = for {
      // Create database transactor
      xa <- DatabaseConnection.createTransactor[F](config.database)
      
      // Create S3 writer
      s3Writer <- S3Writer[F](config.s3)
      
    } yield (xa, s3Writer)

    resources.use { case (xa, s3Writer) =>
      for {
        _ <- Async[F].delay(logger.info("Resources initialized, starting data extraction"))
        
        // Create repository
        repo = DataRepository[F](xa)
        
        // Stream data from database
        dataStream = repo.streamData(config.etl.query, config.etl.batchSize)
        
        // Generate output path
        outputPath = S3Writer.generateOutputPath(config.s3.prefix)
        
        // Write to S3
        _ <- s3Writer.writeParquet(dataStream, outputPath)
        
        _ <- Async[F].delay(logger.info("ETL Pipeline completed successfully"))
      } yield ()
    }.handleErrorWith { error =>
      Async[F].delay(logger.error("ETL Pipeline failed", error)) *>
        Async[F].raiseError(error)
    }
  }
}

object EtlPipeline {
  def apply[F[_]: Async](config: AppConfig): EtlPipeline[F] = 
    new EtlPipeline[F](config)
}
