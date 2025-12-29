package com.async2databricks.s3

import cats.effect.*
import cats.implicits.*
import com.async2databricks.config.S3Config
import com.async2databricks.model.SampleData
import com.async2databricks.utils.CatsLogger
import com.github.mjakubowski84.parquet4s.ParquetWriter
import com.github.mjakubowski84.parquet4s.Path as ParquetPath
import fs2.Stream
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException

trait S3Writer[F[_]] {

  /** Write a stream of data to S3 as Parquet
    */
  def writeParquet(data: Stream[F, SampleData], outputPath: String): F[Unit]
}

object S3Writer extends CatsLogger {

  /** Creates an S3 client configured for LocalStack or AWS
    */
  def createS3Client[F[_]: Async](config: S3Config): Resource[F, S3Client] = {
    val log = logger[F]
    Resource.make {
      Async[F].delay {
        val credentialsProvider = StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.accessKey, config.secretKey)
        )

        val builder = S3Client
          .builder()
          .credentialsProvider(credentialsProvider)
          .region(Region.of(config.region))

        // Use custom endpoint for LocalStack
        val client =
          if (
            config.endpoint.nonEmpty && config.endpoint != "https://s3.amazonaws.com"
          ) {
            builder.endpointOverride(URI.create(config.endpoint)).build()
          } else {
            builder.build()
          }

        // Log effectfully
        log.info(
          s"S3 client created for endpoint: ${config.endpoint}"
        ) // Only for side-effect, not recommended in prod
        client
      }
    }(client => Async[F].delay(client.close()))
  }

  /** Ensures the S3 bucket exists, creates it if not
    */
  def ensureBucket[F[_]: Async](
      s3Client: S3Client,
      bucketName: String
  ): F[Unit] = {
    val log = logger[F]
    Async[F].delay {
      try {
        s3Client.headBucket(
          HeadBucketRequest.builder().bucket(bucketName).build()
        )
        log.info(
          s"Bucket $bucketName already exists"
        ) // Only for side-effect, not recommended in prod
      } catch {
        case _: NoSuchBucketException =>
          log.info(s"Creating bucket $bucketName")
          s3Client.createBucket(
            CreateBucketRequest.builder().bucket(bucketName).build()
          )
          log.info(s"Bucket $bucketName created successfully")
      }
    }
  }

  def apply[F[_]: Async](config: S3Config): Resource[F, S3Writer[F]] = {
    val log = logger[F]
    Resource.eval(
      new S3Writer[F] {
        override def writeParquet(
            data: Stream[F, SampleData],
            outputPath: String
        ): F[Unit] = {
          // Configure Hadoop for S3 access
          val hadoopConf = new org.apache.hadoop.conf.Configuration()
          hadoopConf.set("fs.s3a.access.key", config.accessKey)
          hadoopConf.set("fs.s3a.secret.key", config.secretKey)
          hadoopConf.set("fs.s3a.endpoint", config.endpoint)
          hadoopConf.set("fs.s3a.path.style.access", "true")
          hadoopConf.set(
            "fs.s3a.impl",
            "org.apache.hadoop.fs.s3a.S3AFileSystem"
          )
          hadoopConf.set(
            "fs.s3a.connection.ssl.enabled",
            "false"
          ) // For LocalStack
          for {
            _ <- log.info(
              s"Writing parquet data to: s3://${config.bucket}/$outputPath"
            )
            _ <- log.debug(
              s"Hadoop configuration set for S3: endpoint=${config.endpoint}"
            )
            records <- data.compile.toList
            _ <-
              if (records.nonEmpty) {
                val path = ParquetPath(s"s3a://${config.bucket}/$outputPath")
                for {
                  _ <- Async[F].blocking {
                    ParquetWriter
                      .of[SampleData]
                      .options(ParquetWriter.Options(hadoopConf = hadoopConf))
                      .writeAndClose(path, records)
                  }
                  _ <- log.info(
                    s"Successfully wrote ${records.size} records to $outputPath"
                  )
                } yield ()
              } else {
                log.warn("No records to write")
              }
          } yield ()
        }
      }.pure[F]
    )
  }

  /** Generate a timestamped output path
    */
  def generateOutputPath(prefix: String): String = {
    val timestamp = LocalDateTime
      .now()
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
    s"$prefix/data-$timestamp.parquet"
  }
}
