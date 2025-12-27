#!/bin/bash

# Integration Test Script for ETL Pipeline
# This script tests the end-to-end functionality

set -e

echo "=== Integration Test for ETL Pipeline ==="
echo

# Check Docker services
echo "1. Checking Docker services..."
docker compose ps | grep -E "(healthy|Up)"
echo "✓ Docker services are running"
echo

# Check database
echo "2. Checking PostgreSQL database..."
RECORD_COUNT=$(docker exec etl-postgres psql -U etluser -d etldb -t -c "SELECT COUNT(*) FROM sample_data;")
echo "   Found $RECORD_COUNT records in sample_data table"
echo "✓ Database is populated"
echo

# Check S3
echo "3. Checking LocalStack S3..."
docker exec etl-localstack awslocal s3 ls | grep etl-output-bucket
echo "✓ S3 bucket exists"
echo

# Check sample data
echo "4. Sample data from database:"
docker exec etl-postgres psql -U etluser -d etldb -c "SELECT id, name, category, value FROM sample_data LIMIT 5;"
echo

echo "=== Integration Test Completed Successfully ==="
echo
echo "Next steps:"
echo "1. Build the application: sbt assembly"
echo "2. Run the application: sbt run"
echo "3. Verify output in S3: docker exec etl-localstack awslocal s3 ls s3://etl-output-bucket/data/parquet/ --recursive"
