package com.async2databricks.config

import pureconfig.*

case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: String,
    poolSize: Int
) derives ConfigReader

case class S3Config(
    bucket: String,
    prefix: String,
    endpoint: String,
    region: String,
    accessKey: String,
    secretKey: String
) derives ConfigReader

case class EtlConfig(
    batchSize: Int,
    query: String
) derives ConfigReader

case class AppConfig(
    database: DatabaseConfig,
    s3: S3Config,
    etl: EtlConfig
) derives ConfigReader
