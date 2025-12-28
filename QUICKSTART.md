# Quick Start Guide

This guide will help you get the ETL pipeline running in 5 minutes.

## Prerequisites

- Docker and Docker Compose installed
- Java 11 or later (for local development)
- SBT 1.9.7 (for building the application)

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/akreit/async2databricks.git
cd async2databricks
```

### 2. Start Infrastructure

Start PostgreSQL and LocalStack (S3 emulator):

```bash
docker compose up -d
```

Wait about 15 seconds for services to be healthy:

```bash
docker compose ps
```

You should see both `etl-postgres` and `etl-localstack` as `healthy`.

### 3. Verify Setup

Run the integration test script:

```bash
./docker/integration-test.sh
```

This will verify:
- Docker services are running
- Database has 10 sample records
- S3 bucket exists

### 4. Build the Application

```bash
sbt compile
```

Or build a fat JAR:

```bash
sbt assembly
```

The JAR will be at `target/scala-2.13/async2databricks-assembly-0.1.0.jar`.

### 5. Run the ETL Pipeline

**Option A: Using SBT**

```bash
sbt run
```

**Option B: Using the JAR**

```bash
java -jar target/scala-2.13/async2databricks-assembly-0.1.0.jar
```

### 6. Verify Results

Check that data was written to S3:

```bash
docker exec etl-localstack awslocal s3 ls s3://etl-output-bucket/data/parquet/ --recursive
```

You should see a `.parquet` file with a timestamp.

Download and inspect the file (optional):

```bash
docker exec etl-localstack awslocal s3 cp s3://etl-output-bucket/data/parquet/<filename>.parquet /tmp/output.parquet
docker cp etl-localstack:/tmp/output.parquet ./output.parquet
```

## What's Next?

### Customize the Data Model

Edit `src/main/scala/com/async2databricks/model/SampleData.scala` to match your database schema.

### Update the Query

Modify the query in `src/main/resources/application.conf`:

```hocon
etl {
  batch-size = 1000
  query = "SELECT * FROM your_table WHERE ..."
}
```

### Connect to Your Database

Update database credentials in `src/main/resources/application.conf`:

```hocon
database {
  url = "jdbc:postgresql://your-host:5432/your-database"
  user = "your-username"
  password = "your-password"
}
```

### Deploy to AWS

See the main [README.md](README.md) for detailed AWS deployment instructions.

## Troubleshooting

### Services Not Starting

```bash
docker compose logs postgres
docker compose logs localstack
```

### Connection Refused

Make sure services are healthy:

```bash
docker compose ps
```

Both should show `(healthy)` status.

### Out of Memory

Increase heap size:

```bash
java -Xmx4g -jar target/scala-2.13/async2databricks-assembly-0.1.0.jar
```

### Clean Start

To start fresh:

```bash
docker compose down -v
docker compose up -d
```

This removes volumes and recreates everything.

## Running Tests

```bash
sbt test
```

## Cleaning Up

Stop and remove containers:

```bash
docker compose down
```

Remove volumes too:

```bash
docker compose down -v
```

## Architecture Overview

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│  PostgreSQL │ ──────> │ ETL Pipeline │ ──────> │     S3      │
│  (Source)   │  Doobie │   (Scala)    │ Parquet │ (Destination)│
└─────────────┘         └──────────────┘         └─────────────┘
      │                        │
      │                    FS2 Stream
      │                    Cats Effect
      └────────────────────────┘
```

The pipeline:
1. Connects to PostgreSQL using Doobie
2. Streams data efficiently using FS2
3. Batches records for optimal performance
4. Writes to S3 in Parquet format using Parquet4s
5. Configuration managed by PureConfig

## Configuration

All configuration is in `src/main/resources/application.conf`. You can override values using:

**System Properties:**

```bash
sbt run -Ddatabase.url=jdbc:postgresql://newhost:5432/db
```

**Environment Variables:**

```bash
export DATABASE_URL=jdbc:postgresql://newhost:5432/db
sbt run
```

## Next Steps

- Read the full [README.md](README.md) for deployment guides
- Explore the code in `src/main/scala/com/async2databricks/`
- Customize for your use case
- Deploy to AWS
