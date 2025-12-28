#!/bin/bash

# Wait for LocalStack to be ready
echo "Waiting for LocalStack to be ready..."
sleep 5

# Create S3 bucket
echo "Creating S3 bucket: etl-output-bucket"
awslocal s3 mb s3://etl-output-bucket

echo "S3 initialization complete"
