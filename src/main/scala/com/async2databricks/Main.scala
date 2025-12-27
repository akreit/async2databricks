package com.async2databricks

import cats.effect._
import com.async2databricks.config.AppConfig
import com.async2databricks.etl.EtlPipeline
import com.typesafe.scalalogging.LazyLogging

object Main extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("Application starting...")
    
    val program = for {
      // Load configuration
      config <- IO.fromEither(AppConfig.load().left.map(failures => 
        new RuntimeException(s"Configuration error: ${failures.toList.mkString(", ")}")
      ))
        .handleErrorWith { error =>
          IO.delay(logger.error("Failed to load configuration", error)) *>
            IO.raiseError(error)
        }
      
      _ <- IO.delay(logger.info("Configuration loaded successfully"))
      _ <- IO.delay(logger.info(s"Database: ${config.database.url}"))
      _ <- IO.delay(logger.info(s"S3 Bucket: ${config.s3.bucket}"))
      
      // Run ETL pipeline
      pipeline = EtlPipeline[IO](config)
      _ <- pipeline.run()
      
    } yield ExitCode.Success

    program.handleErrorWith { error =>
      IO.delay(logger.error("Application failed", error)) *>
        IO.pure(ExitCode.Error)
    }
  }
}
