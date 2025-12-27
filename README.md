# async2databricks

A lightweight, streaming ETL pipeline in Scala for loading data from PostgreSQL to cloud storage (S3/Databricks) using Parquet format.

## Features

- **Stream Processing**: Uses FS2 and Doobie for efficient streaming from PostgreSQL
- **Parquet Format**: Writes data in columnar Parquet format for optimal analytics performance
- **Type-Safe Configuration**: PureConfig for compile-time configuration validation
- **Cloud-Ready**: Supports both AWS S3 and local development with LocalStack
- **Docker Support**: Complete Docker setup for local development and testing
- **Functional Programming**: Built with Cats Effect for pure functional effects

## Architecture

```
PostgreSQL → Doobie (FS2 Stream) → Parquet4s → S3/Cloud Storage
```

The pipeline:
1. Connects to PostgreSQL using Doobie with Hikari connection pooling
2. Streams data efficiently using FS2 streams
3. Batches records for optimal processing
4. Writes to S3 in Parquet format using Parquet4s
5. All configuration loaded via PureConfig

## Prerequisites

- **Local Development**:
  - Docker and Docker Compose
  - Java 11 or later
  - SBT 1.9.7

- **AWS Deployment**:
  - AWS Account with S3 access
  - EC2 instance or container runtime (ECS/EKS)
  - PostgreSQL database (RDS or self-hosted)

## Local Development Setup

### 1. Start Infrastructure

Start PostgreSQL and LocalStack (S3 emulator):

```bash
docker-compose up -d
```

This will:
- Start PostgreSQL on port 5432 with sample data
- Start LocalStack S3 on port 4566
- Automatically create the `etl-output-bucket` S3 bucket
- Initialize the database with sample data

### 2. Build the Application

```bash
sbt compile
```

### 3. Run Tests

```bash
sbt test
```

### 4. Run the ETL Pipeline

```bash
sbt run
```

Or build and run a fat JAR:

```bash
sbt assembly
java -jar target/scala-2.13/async2databricks-assembly-0.1.0.jar
```

### 5. Verify Results

Check LocalStack S3 for output files:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://etl-output-bucket/data/parquet/ --recursive
```

Download and inspect a parquet file:

```bash
aws --endpoint-url=http://localhost:4566 s3 cp s3://etl-output-bucket/data/parquet/data-<timestamp>.parquet ./output.parquet
```

## Configuration

Configuration is managed via `src/main/resources/application.conf`:

```hocon
database {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://localhost:5432/etldb"
  user = "etluser"
  password = "etlpass"
  pool-size = 10
}

s3 {
  bucket = "etl-output-bucket"
  prefix = "data/parquet/"
  endpoint = "http://localhost:4566"  # LocalStack for local
  region = "us-east-1"
  access-key = "test"
  secret-key = "test"
}

etl {
  batch-size = 1000
  query = "SELECT * FROM sample_data"
}
```

### Environment-Specific Configuration

For production/AWS deployment, override configuration using environment variables or system properties:

```bash
java -Ddatabase.url=jdbc:postgresql://prod-db:5432/proddb \
     -Ds3.endpoint="" \
     -Ds3.access-key=$AWS_ACCESS_KEY_ID \
     -Ds3.secret-key=$AWS_SECRET_ACCESS_KEY \
     -jar async2databricks-assembly-0.1.0.jar
```

## AWS Deployment

### Option 1: EC2 Deployment

#### 1. Prerequisites

- EC2 instance with Java 11+ installed
- Security group allowing outbound access to RDS and S3
- IAM role with S3 write permissions attached to EC2 instance

#### 2. Build Application

Build the fat JAR locally:

```bash
sbt assembly
```

#### 3. Upload to EC2

```bash
scp target/scala-2.13/async2databricks-assembly-0.1.0.jar ec2-user@<instance-ip>:~/
scp src/main/resources/application.conf ec2-user@<instance-ip>:~/application.conf
```

#### 4. Configure for Production

Edit `application.conf` on EC2:

```hocon
database {
  url = "jdbc:postgresql://<rds-endpoint>:5432/<database>"
  user = "<db-user>"
  password = "<db-password>"
}

s3 {
  bucket = "<your-s3-bucket>"
  endpoint = ""  # Empty for AWS S3
  region = "us-east-1"
}
```

#### 5. Run

```bash
java -jar async2databricks-assembly-0.1.0.jar
```

### Option 2: ECS/Fargate Deployment

#### 1. Build Docker Image

```bash
docker build -t async2databricks:latest .
```

#### 2. Push to ECR

```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
docker tag async2databricks:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks:latest
```

#### 3. Create ECS Task Definition

```json
{
  "family": "async2databricks",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "etl-pipeline",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks:latest",
      "environment": [
        {
          "name": "DATABASE_URL",
          "value": "jdbc:postgresql://<rds-endpoint>:5432/<database>"
        },
        {
          "name": "S3_BUCKET",
          "value": "<your-bucket>"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/async2databricks",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ],
  "taskRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskRole",
  "executionRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskExecutionRole"
}
```

#### 4. Run Task

```bash
aws ecs run-task \
  --cluster <cluster-name> \
  --task-definition async2databricks \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[<subnet-id>],securityGroups=[<sg-id>],assignPublicIp=ENABLED}"
```

### Option 3: Scheduled Execution with EventBridge

For recurring ETL jobs:

1. Create an EventBridge rule (e.g., daily at 2 AM):

```bash
aws events put-rule \
  --name async2databricks-daily \
  --schedule-expression "cron(0 2 * * ? *)"
```

2. Configure ECS task as target:

```bash
aws events put-targets \
  --rule async2databricks-daily \
  --targets "Id"="1","Arn"="arn:aws:ecs:us-east-1:<account-id>:cluster/<cluster>","RoleArn"="<role-arn>","EcsParameters"="{TaskDefinitionArn=<task-def-arn>,LaunchType=FARGATE,NetworkConfiguration={awsvpcConfiguration={Subnets=[<subnet>],SecurityGroups=[<sg>],AssignPublicIp=ENABLED}}}"
```

## IAM Permissions

The application requires the following AWS permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::<your-bucket>/*",
        "arn:aws:s3:::<your-bucket>"
      ]
    }
  ]
}
```

## Database Schema

The application expects a table matching this schema (customize as needed):

```sql
CREATE TABLE sample_data (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    category VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Update the data model in `src/main/scala/com/async2databricks/model/SampleData.scala` to match your schema.

## Project Structure

```
.
├── build.sbt                       # SBT build configuration
├── docker-compose.yml              # Local development infrastructure
├── Dockerfile                      # Application container
├── docker/
│   ├── init.sql                   # PostgreSQL initialization
│   └── init-s3.sh                 # LocalStack S3 setup
└── src/
    ├── main/
    │   ├── resources/
    │   │   ├── application.conf   # Application configuration
    │   │   └── logback.xml        # Logging configuration
    │   └── scala/com/async2databricks/
    │       ├── Main.scala         # Application entry point
    │       ├── config/            # Configuration models
    │       ├── database/          # Doobie database layer
    │       ├── etl/               # ETL pipeline orchestration
    │       ├── model/             # Domain models
    │       └── s3/                # S3/Parquet writer
    └── test/
        └── scala/com/async2databricks/  # Unit tests
```

## Development

### Adding Dependencies

Edit `build.sbt` and run:

```bash
sbt update
```

### Code Formatting

```bash
sbt scalafmt
```

### Running Specific Tests

```bash
sbt "testOnly com.async2databricks.config.AppConfigSpec"
```

## Troubleshooting

### Connection Issues

- **PostgreSQL**: Ensure Docker containers are running: `docker-compose ps`
- **LocalStack**: Check S3 endpoint: `curl http://localhost:4566/_localstack/health`

### Memory Issues

Increase JVM heap size:

```bash
java -Xmx4g -jar async2databricks-assembly-0.1.0.jar
```

### Debugging

Enable debug logging in `src/main/resources/logback.xml`:

```xml
<logger name="com.async2databricks" level="DEBUG" />
<logger name="doobie" level="DEBUG" />
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

See LICENSE file for details.
