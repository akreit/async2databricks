package com.async2databricks.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

class AppConfigSpec extends AnyFlatSpec with Matchers {

  "AppConfig" should "load from configuration file" in {
    val config = AppConfig.load()
    config.isRight shouldBe true
    
    val appConfig = config.right.get
    appConfig.database.driver shouldBe "org.postgresql.Driver"
    appConfig.database.poolSize shouldBe 10
    appConfig.s3.bucket shouldBe "etl-output-bucket"
    appConfig.etl.batchSize shouldBe 1000
  }

  it should "have valid database configuration" in {
    val config = AppConfig.load().right.get
    config.database.url should include("postgresql")
    config.database.user should not be empty
    config.database.password should not be empty
  }

  it should "have valid S3 configuration" in {
    val config = AppConfig.load().right.get
    config.s3.bucket should not be empty
    config.s3.region should not be empty
  }

  it should "have valid ETL configuration" in {
    val config = AppConfig.load().right.get
    config.etl.batchSize should be > 0
    config.etl.query should not be empty
  }
}
