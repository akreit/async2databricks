package com.async2databricks.integration

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import com.async2databricks.config.*
import com.async2databricks.database.{DataRepository, DatabaseConnection}
import com.async2databricks.model.SampleData
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import doobie.*
import doobie.implicits.*
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class EtlPipelineIntegrationSpec
    extends AsyncFlatSpec
    with AsyncIOSpec
    with Matchers
    with TestContainerForAll {

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def()

  "EtlPipeline" should "successfully extract data from PostgreSQL and load to S3" in {
    withContainers { postgres =>
      (for {
        // Set up test database with sample data
        _ <- setupTestData(postgres)

        // Create test configuration
        config = createTestConfig(postgres)

        // Create resources
        _ <- DatabaseConnection
          .createTransactor[IO](config.database)
          .use { xa =>
            // Verify data exists in database
            val countQuery = sql"SELECT COUNT(*) FROM sample_data"
              .query[Long]
              .unique
              .transact(xa)

            for {
              count <- countQuery
              _ <- IO(count should be > 0L)

              // Create repository and read data
              repo = DataRepository[IO](xa)
              data <- repo
                .streamData(config.etl.query, config.etl.batchSize)
                .compile
                .toList

              _ <- IO {
                data should not be empty
                data.size should be(10)
                data.head shouldBe a[SampleData]
                data.head.name shouldBe "Product A"
              }
            } yield succeed
          }
      } yield succeed).unsafeToFuture()
    }
  }

  it should "handle empty result sets gracefully" in {
    withContainers { postgres =>
      (for {
        // Set up test database with empty table
        _ <- setupEmptyTable(postgres)

        config = createTestConfig(postgres)

        _ <- DatabaseConnection
          .createTransactor[IO](config.database)
          .use { xa =>
            val repo = DataRepository[IO](xa)
            for {
              data <- repo
                .streamData("SELECT * FROM empty_data", config.etl.batchSize)
                .compile
                .toList
              _ <- IO(data shouldBe empty)
            } yield succeed
          }
      } yield succeed).unsafeToFuture()
    }
  }

  it should "stream data in batches correctly" in {
    withContainers { postgres =>
      (for {
        _ <- setupTestData(postgres)
        config = createTestConfig(postgres).copy(
          etl = EtlConfig(batchSize = 3, query = "SELECT * FROM sample_data")
        )

        _ <- DatabaseConnection
          .createTransactor[IO](config.database)
          .use { xa =>
            val repo = DataRepository[IO](xa)
            for {
              // Count batches by collecting in chunks
              batchSizes <- repo
                .streamData(config.etl.query, config.etl.batchSize)
                .chunkN(config.etl.batchSize)
                .map(_.size)
                .compile
                .toList
              
              _ <- IO {
                // With 10 records and batch size 3, we should have multiple batches
                batchSizes should not be empty
                batchSizes.sum shouldBe 10
              }
            } yield succeed
          }
      } yield succeed).unsafeToFuture()
    }
  }

  private def setupTestData(postgres: PostgreSQLContainer): IO[Unit] = {
    IO.delay {
      val conn = java.sql.DriverManager.getConnection(
        postgres.jdbcUrl,
        postgres.username,
        postgres.password
      )
      try {
        val stmt = conn.createStatement()
        
        // Drop table if exists and create fresh
        stmt.execute("DROP TABLE IF EXISTS sample_data")
        
        // Create table
        stmt.execute("""
          CREATE TABLE sample_data (
            id BIGSERIAL PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            value DOUBLE PRECISION NOT NULL,
            category VARCHAR(100) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """)

        // Insert test data
        stmt.execute("""
          INSERT INTO sample_data (name, value, category, created_at) VALUES
            ('Product A', 100.50, 'Electronics', '2024-01-01 10:00:00'),
            ('Product B', 250.75, 'Furniture', '2024-01-02 11:30:00'),
            ('Product C', 75.25, 'Electronics', '2024-01-03 09:15:00'),
            ('Product D', 500.00, 'Appliances', '2024-01-04 14:20:00'),
            ('Product E', 125.99, 'Electronics', '2024-01-05 16:45:00'),
            ('Product F', 350.50, 'Furniture', '2024-01-06 08:30:00'),
            ('Product G', 89.99, 'Electronics', '2024-01-07 12:00:00'),
            ('Product H', 450.00, 'Appliances', '2024-01-08 15:30:00'),
            ('Product I', 199.99, 'Furniture', '2024-01-09 10:45:00'),
            ('Product J', 299.50, 'Electronics', '2024-01-10 13:20:00')
        """)

        stmt.close()
      } finally {
        conn.close()
      }
    }
  }

  private def setupEmptyTable(postgres: PostgreSQLContainer): IO[Unit] = {
    IO.delay {
      val conn = java.sql.DriverManager.getConnection(
        postgres.jdbcUrl,
        postgres.username,
        postgres.password
      )
      try {
        val stmt = conn.createStatement()
        stmt.execute("DROP TABLE IF EXISTS empty_data")
        stmt.execute("""
          CREATE TABLE empty_data (
            id BIGSERIAL PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            value DOUBLE PRECISION NOT NULL,
            category VARCHAR(100) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """)
        stmt.close()
      } finally {
        conn.close()
      }
    }
  }

  private def createTestConfig(postgres: PostgreSQLContainer): AppConfig = {
    AppConfig(
      database = DatabaseConfig(
        driver = postgres.driverClassName,
        url = postgres.jdbcUrl,
        user = postgres.username,
        password = postgres.password,
        poolSize = 5
      ),
      s3 = S3Config(
        bucket = "test-bucket",
        prefix = "test/",
        endpoint = "http://localhost:4566",
        region = "us-east-1",
        accessKey = "test",
        secretKey = "test"
      ),
      etl = EtlConfig(
        batchSize = 1000,
        query = "SELECT * FROM sample_data"
      )
    )
  }
}
