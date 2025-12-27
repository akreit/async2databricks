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
  s3: S3Config,  // renamed field in config
  etl: EtlConfig
)

object AppConfig {
  def load(): Either[pureconfig.error.ConfigReaderFailures, AppConfig] = {
    import pureconfig.generic.ProductHint
    import pureconfig.{ConfigFieldMapping, KebabCase}
    
    // For all types, use kebab-case
    implicit def hint[T]: ProductHint[T] = ProductHint[T](
      fieldMapping = ConfigFieldMapping(KebabCase, KebabCase),
      allowUnknownKeys = false
    )
    
    ConfigSource.default.at("").load[AppConfig]
  }
}
