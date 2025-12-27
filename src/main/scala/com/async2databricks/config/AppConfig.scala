package com.async2databricks.config

import pureconfig._
import pureconfig.generic.auto._

case class DatabaseConfig(
  driver: String,
  url: String,
  user: String,
  password: String,
  poolSize: Int
)

case class S3Config(
  bucket: String,
  prefix: String,
  endpoint: String,
  region: String,
  accessKey: String,
  secretKey: String
)

case class EtlConfig(
  batchSize: Int,
  query: String
)

case class AppConfig(
  database: DatabaseConfig,
  s3: S3Config,
  etl: EtlConfig
)

object AppConfig {
  def load(): Either[pureconfig.error.ConfigReaderFailures, AppConfig] = {
    ConfigSource.default.load[AppConfig]
  }
}
