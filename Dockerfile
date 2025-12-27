FROM hseeberger/scala-sbt:11.0.12_1.5.5_2.13.6 AS builder

WORKDIR /app

# Copy build files
COPY build.sbt .
COPY project project

# Download dependencies
RUN sbt update

# Copy source code
COPY src src

# Build the application
RUN sbt assembly

# Runtime stage
FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the fat JAR from builder
COPY --from=builder /app/target/scala-2.13/async2databricks-assembly-0.1.0.jar /app/app.jar

# Copy configuration
COPY src/main/resources/application.conf /app/application.conf

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
