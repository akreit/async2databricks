package com.async2databricks.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AppConfigSpec extends AnyFlatSpec with Matchers {

  "DatabaseConfig" should "be created with valid values" in {
    val dbConfig = DatabaseConfig(
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql://localhost:5432/test",
      user = "test",
      password = "test",
      poolSize = 10
    )
    
    dbConfig.driver shouldBe "org.postgresql.Driver"
    dbConfig.poolSize should be > 0
  }

  "S3Config" should "be created with valid values" in {
    val s3Config = S3Config(
      bucket = "test-bucket",
      prefix = "prefix/",
      endpoint = "http://localhost:4566",
      region = "us-east-1",
      accessKey = "test",
      secretKey = "test"
    )
    
    s3Config.bucket should not be empty
    s3Config.region should not be empty
  }

  "EtlConfig" should "be created with valid values" in {
    val etlConfig = EtlConfig(
      batchSize = 1000,
      query = "SELECT * FROM test"
    )
    
    etlConfig.batchSize should be > 0
    etlConfig.query should not be empty
  }
  
  "AppConfig" should "be created with all components" in {
    val appConfig = AppConfig(
      database = DatabaseConfig("driver", "url", "user", "pass", 10),
      s3 = S3Config("bucket", "prefix/", "endpoint", "region", "key", "secret"),
      etl = EtlConfig(1000, "SELECT *")
    )
    
    appConfig.database should not be null
    appConfig.s3 should not be null
    appConfig.etl should not be null
  }
}
