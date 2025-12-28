package com.async2databricks

import cats.effect.*
import com.async2databricks.config.AppConfig
import com.async2databricks.etl.EtlPipeline
import com.async2databricks.utils.CatsLogger
import com.async2databricks.utils.SafeFileOps.*

/** main entry point for the application
  */
object Main extends IOApp with CatsLogger {

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("Application starting...")

    val program = for {
      // Load configuration
      config <- loadConfig[AppConfig]("application.conf")

      _ <- logger.info("Configuration loaded successfully")
      _ <- logger.info(s"Database: ${config.database.url}")
      _ <- logger.info(s"S3 Bucket: ${config.s3.bucket}")

      // Run ETL pipeline
      pipeline = EtlPipeline[IO](config)
      _ <- pipeline.run()

    } yield ExitCode.Success

    program
      .handleErrorWith { error =>
        logger
          .error(s"Application failed with error: ${error.getMessage}")
          .as(ExitCode.Error)
      }
  }
}
