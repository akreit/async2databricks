# AWS Deployment Guide

This guide provides detailed instructions for deploying the ETL pipeline to AWS.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Option 1: EC2 Deployment](#option-1-ec2-deployment)
4. [Option 2: ECS/Fargate Deployment](#option-2-ecsfargate-deployment)
5. [Option 3: AWS Lambda (Serverless)](#option-3-aws-lambda-serverless)
6. [Scheduled Execution](#scheduled-execution)
7. [Monitoring](#monitoring)
8. [Cost Optimization](#cost-optimization)

## Prerequisites

- AWS Account with appropriate permissions
- AWS CLI configured
- RDS PostgreSQL database (or accessible PostgreSQL instance)
- S3 bucket for output data
- IAM roles and policies configured

## Architecture Overview

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│  RDS        │ ──────> │ ECS/EC2/     │ ──────> │     S3      │
│  PostgreSQL │         │ Lambda       │         │   Bucket    │
└─────────────┘         └──────────────┘         └─────────────┘
                               │
                        ┌──────┴──────┐
                        │ CloudWatch  │
                        │   Logs      │
                        └─────────────┘
```

## Option 1: EC2 Deployment

### 1.1. Set Up RDS PostgreSQL

```bash
# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier async2databricks-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --master-username admin \
  --master-user-password YOUR_PASSWORD \
  --allocated-storage 20 \
  --vpc-security-group-ids sg-xxxxx \
  --db-subnet-group-name your-subnet-group \
  --backup-retention-period 7 \
  --publicly-accessible false
```

### 1.2. Create S3 Bucket

```bash
aws s3 mb s3://your-etl-output-bucket
```

### 1.3. Create IAM Role for EC2

Create `ec2-etl-role-policy.json`:

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
        "arn:aws:s3:::your-etl-output-bucket/*",
        "arn:aws:s3:::your-etl-output-bucket"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
```

Create the role:

```bash
aws iam create-role \
  --role-name async2databricks-ec2-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ec2.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

aws iam put-role-policy \
  --role-name async2databricks-ec2-role \
  --policy-name etl-permissions \
  --policy-document file://ec2-etl-role-policy.json

aws iam create-instance-profile \
  --instance-profile-name async2databricks-profile

aws iam add-role-to-instance-profile \
  --instance-profile-name async2databricks-profile \
  --role-name async2databricks-ec2-role
```

### 1.4. Launch EC2 Instance

```bash
aws ec2 run-instances \
  --image-id ami-xxxxx \
  --instance-type t3.small \
  --key-name your-key-pair \
  --security-group-ids sg-xxxxx \
  --subnet-id subnet-xxxxx \
  --iam-instance-profile Name=async2databricks-profile \
  --user-data file://user-data.sh \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=async2databricks-etl}]'
```

Create `user-data.sh`:

```bash
#!/bin/bash
yum update -y
yum install -y java-11-amazon-corretto
mkdir -p /opt/etl
cd /opt/etl
# Download your JAR from S3 or build it
```

### 1.5. Deploy Application

Build the JAR:

```bash
sbt assembly
```

Copy to EC2:

```bash
scp -i your-key.pem \
  target/scala-2.13/async2databricks-assembly-0.1.0.jar \
  ec2-user@<instance-ip>:/opt/etl/
```

Create production configuration on EC2 at `/opt/etl/application.conf`:

```hocon
database {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://your-rds-endpoint.rds.amazonaws.com:5432/database"
  user = "admin"
  password = "YOUR_PASSWORD"
  pool-size = 10
}

s3 {
  bucket = "your-etl-output-bucket"
  prefix = "data/parquet/"
  endpoint = ""  # Empty for AWS S3
  region = "us-east-1"
  access-key = ""  # Use IAM role
  secret-key = ""  # Use IAM role
}

etl {
  batch-size = 1000
  query = "SELECT * FROM your_table"
}
```

### 1.6. Run the Application

```bash
ssh -i your-key.pem ec2-user@<instance-ip>
cd /opt/etl
java -Xmx2g -Dconfig.file=application.conf -jar async2databricks-assembly-0.1.0.jar
```

## Option 2: ECS/Fargate Deployment

### 2.1. Create ECR Repository

```bash
aws ecr create-repository --repository-name async2databricks
```

### 2.2. Build and Push Docker Image

```bash
# Build the application
sbt assembly

# Build Docker image
docker build -t async2databricks:latest .

# Tag and push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

docker tag async2databricks:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks:latest

docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks:latest
```

### 2.3. Create ECS Task Definition

Create `task-definition.json`:

```json
{
  "family": "async2databricks",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "taskRoleArn": "arn:aws:iam::<account-id>:role/async2databricks-task-role",
  "executionRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "etl-pipeline",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks:latest",
      "essential": true,
      "environment": [
        {
          "name": "DATABASE_URL",
          "value": "jdbc:postgresql://your-rds-endpoint.rds.amazonaws.com:5432/database"
        },
        {
          "name": "DATABASE_USER",
          "value": "admin"
        },
        {
          "name": "S3_BUCKET",
          "value": "your-etl-output-bucket"
        },
        {
          "name": "S3_REGION",
          "value": "us-east-1"
        }
      ],
      "secrets": [
        {
          "name": "DATABASE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:etl/db/password"
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
  ]
}
```

Register the task:

```bash
aws ecs register-task-definition --cli-input-json file://task-definition.json
```

### 2.4. Create ECS Cluster

```bash
aws ecs create-cluster --cluster-name async2databricks-cluster
```

### 2.5. Run Task

```bash
aws ecs run-task \
  --cluster async2databricks-cluster \
  --task-definition async2databricks \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx],securityGroups=[sg-xxxxx],assignPublicIp=DISABLED}"
```

## Option 3: AWS Lambda (Serverless)

For smaller datasets or infrequent runs:

### 3.1. Prepare Lambda Package

Due to size constraints, Lambda may not be ideal for large JARs. Consider using:
- Lambda Container Images (up to 10GB)
- Lambda Layers for dependencies
- Or stick with EC2/ECS for Java applications

### 3.2. Create Lambda Function (with Container)

```bash
# Build container image
docker build -f Dockerfile.lambda -t async2databricks-lambda .

# Push to ECR
docker tag async2databricks-lambda:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks-lambda:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks-lambda:latest

# Create Lambda function
aws lambda create-function \
  --function-name async2databricks-etl \
  --package-type Image \
  --code ImageUri=<account-id>.dkr.ecr.us-east-1.amazonaws.com/async2databricks-lambda:latest \
  --role arn:aws:iam::<account-id>:role/lambda-etl-role \
  --timeout 900 \
  --memory-size 3008
```

## Scheduled Execution

### Using EventBridge (formerly CloudWatch Events)

#### For EC2:

Create a cron job:

```bash
# On EC2 instance
crontab -e

# Add: Run daily at 2 AM
0 2 * * * cd /opt/etl && java -jar async2databricks-assembly-0.1.0.jar >> /var/log/etl.log 2>&1
```

#### For ECS:

```bash
# Create EventBridge rule
aws events put-rule \
  --name async2databricks-daily \
  --schedule-expression "cron(0 2 * * ? *)" \
  --state ENABLED

# Add ECS task as target
aws events put-targets \
  --rule async2databricks-daily \
  --targets "Id"="1","Arn"="arn:aws:ecs:us-east-1:<account-id>:cluster/async2databricks-cluster","RoleArn"="arn:aws:iam::<account-id>:role/ecsEventsRole","EcsParameters"="{TaskDefinitionArn=arn:aws:ecs:us-east-1:<account-id>:task-definition/async2databricks,LaunchType=FARGATE,NetworkConfiguration={awsvpcConfiguration={Subnets=[subnet-xxxxx],SecurityGroups=[sg-xxxxx],AssignPublicIp=DISABLED}}}"
```

#### For Lambda:

```bash
aws events put-rule \
  --name async2databricks-schedule \
  --schedule-expression "rate(1 hour)"

aws lambda add-permission \
  --function-name async2databricks-etl \
  --statement-id async2databricks-schedule \
  --action lambda:InvokeFunction \
  --principal events.amazonaws.com \
  --source-arn arn:aws:events:us-east-1:<account-id>:rule/async2databricks-schedule

aws events put-targets \
  --rule async2databricks-schedule \
  --targets "Id"="1","Arn"="arn:aws:lambda:us-east-1:<account-id>:function:async2databricks-etl"
```

## Monitoring

### CloudWatch Logs

View logs:

```bash
aws logs tail /ecs/async2databricks --follow
```

### CloudWatch Metrics

Create custom metrics in your application:

```scala
// Add AWS SDK dependency for CloudWatch
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model._

def publishMetric(recordCount: Int): Unit = {
  val client = CloudWatchClient.create()
  
  client.putMetricData(
    PutMetricDataRequest.builder()
      .namespace("ETL/Pipeline")
      .metricData(
        MetricDatum.builder()
          .metricName("RecordsProcessed")
          .value(recordCount.toDouble)
          .build()
      )
      .build()
  )
}
```

### Alarms

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name etl-failure \
  --alarm-description "Alert on ETL failures" \
  --metric-name Errors \
  --namespace AWS/ECS \
  --statistic Sum \
  --period 300 \
  --threshold 1 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 1
```

## Cost Optimization

### 1. Use Spot Instances

For non-critical workloads:

```bash
aws ec2 run-instances \
  --instance-market-options MarketType=spot \
  --instance-type t3.small \
  ...
```

### 2. Fargate Spot

```json
{
  "capacityProviderStrategy": [
    {
      "capacityProvider": "FARGATE_SPOT",
      "weight": 1
    }
  ]
}
```

### 3. S3 Lifecycle Policies

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket your-etl-output-bucket \
  --lifecycle-configuration '{
    "Rules": [{
      "Id": "archive-old-data",
      "Status": "Enabled",
      "Transitions": [{
        "Days": 30,
        "StorageClass": "GLACIER"
      }]
    }]
  }'
```

### 4. RDS Reserved Instances

For production, buy reserved instances for cost savings.

## Security Best Practices

1. **Use Secrets Manager** for database credentials
2. **Enable VPC** endpoints for S3 to avoid NAT charges
3. **Use IAM roles** instead of access keys
4. **Enable encryption** for S3 and RDS
5. **Use Security Groups** to restrict access
6. **Enable CloudTrail** for auditing

## Troubleshooting

### Connection Timeout

- Check Security Groups
- Verify VPC configuration
- Ensure RDS is in same VPC or accessible

### Out of Memory

Increase task/instance memory:

```bash
# For ECS
"memory": "4096"

# For EC2
java -Xmx4g -jar app.jar
```

### S3 Access Denied

Verify IAM role has correct permissions.

## Next Steps

1. Set up monitoring and alerting
2. Implement data quality checks
3. Add incremental loading
4. Configure backup and disaster recovery
5. Set up CI/CD pipeline

## Additional Resources

- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS RDS Documentation](https://docs.aws.amazon.com/rds/)
- [AWS S3 Best Practices](https://docs.aws.amazon.com/s3/)
