package com.async2databricks.s3

import cats.effect._
import cats.implicits._
import fs2.Stream
import com.github.mjakubowski84.parquet4s.{ParquetWriter, Path => ParquetPath}
import com.async2databricks.config.S3Config
import com.async2databricks.model.SampleData
import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, HeadBucketRequest, NoSuchBucketException}
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

trait S3Writer[F[_]] {
  /**
   * Write a stream of data to S3 as Parquet
   */
  def writeParquet(data: Stream[F, SampleData], outputPath: String): F[Unit]
}

object S3Writer extends LazyLogging {

  /**
   * Creates an S3 client configured for LocalStack or AWS
   */
  def createS3Client(config: S3Config): Resource[IO, S3Client] = {
    Resource.make {
      IO.delay {
        val credentialsProvider = StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.accessKey, config.secretKey)
        )

        val builder = S3Client.builder()
          .credentialsProvider(credentialsProvider)
          .region(Region.of(config.region))

        // Use custom endpoint for LocalStack
        val client = if (config.endpoint.nonEmpty && config.endpoint != "https://s3.amazonaws.com") {
          builder.endpointOverride(URI.create(config.endpoint))
            .build()
        } else {
          builder.build()
        }

        logger.info(s"S3 client created for endpoint: ${config.endpoint}")
        client
      }
    }(client => IO.delay(client.close()))
  }

  /**
   * Ensures the S3 bucket exists, creates it if not
   */
  def ensureBucket(s3Client: S3Client, bucketName: String): IO[Unit] = {
    IO.delay {
      try {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
        logger.info(s"Bucket $bucketName already exists")
      } catch {
        case _: NoSuchBucketException =>
          logger.info(s"Creating bucket $bucketName")
          s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
          logger.info(s"Bucket $bucketName created successfully")
      }
    }
  }

  def apply[F[_]: Async](config: S3Config): Resource[F, S3Writer[F]] = {
    Resource.eval(Async[F].delay(new S3Writer[F] {
      override def writeParquet(data: Stream[F, SampleData], outputPath: String): F[Unit] = {
        // Configure Hadoop for S3 access
        val hadoopConf = new org.apache.hadoop.conf.Configuration()
        hadoopConf.set("fs.s3a.access.key", config.accessKey)
        hadoopConf.set("fs.s3a.secret.key", config.secretKey)
        hadoopConf.set("fs.s3a.endpoint", config.endpoint)
        hadoopConf.set("fs.s3a.path.style.access", "true")
        hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        hadoopConf.set("fs.s3a.connection.ssl.enabled", "false") // For LocalStack
        
        Async[F].delay {
          logger.info(s"Writing parquet data to: s3://${config.bucket}/$outputPath")
          logger.debug(s"Hadoop configuration set for S3: endpoint=${config.endpoint}")
        }.flatMap { _ =>
          // Convert stream to list and write
          data.compile.toList.flatMap { records =>
            Async[F].delay {
              if (records.nonEmpty) {
                val path = ParquetPath(s"s3a://${config.bucket}/$outputPath")
                // Use builder API with Hadoop configuration
                ParquetWriter.of[SampleData]
                  .options(ParquetWriter.Options(hadoopConf = hadoopConf))
                  .writeAndClose(path, records)
                logger.info(s"Successfully wrote ${records.size} records to $outputPath")
              } else {
                logger.warn("No records to write")
              }
            }
          }
        }
      }
    }))
  }

  /**
   * Generate a timestamped output path
   */
  def generateOutputPath(prefix: String): String = {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
    s"$prefix/data-$timestamp.parquet"
  }
}
