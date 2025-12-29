name := "async2databricks"


inThisBuild(
  Seq(
    organization := "com.async2databricks",
    scalaVersion := "3.7.4",
    // Compiler options
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all"
    ),
    versionScheme := Some("early-semver"),
    semanticdbEnabled := true,
    semanticdbVersion := "4.13.10"
  )
)
version := "0.1.0"

scalaVersion := "3.7.4"

libraryDependencies ++= Seq(
  // Doobie for database access
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC10",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC10",

  // Parquet4s for Parquet file handling
  "com.github.mjakubowski84" %% "parquet4s-core" % "2.23.0",
  "com.github.mjakubowski84" %% "parquet4s-fs2" % "2.23.0",

  // PureConfig for configuration
  "com.github.pureconfig" %% "pureconfig-core" % "0.17.9",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.9",

  // Cats Effect
  "org.typelevel" %% "cats-effect" % "3.6.3",

  // FS2
  "co.fs2" %% "fs2-core" % "3.12.2",
  "co.fs2" %% "fs2-io" % "3.12.2",

  // AWS S3 SDK
  "software.amazon.awssdk" % "s3" % "2.40.16",

  // Hadoop for S3A filesystem
  "org.apache.hadoop" % "hadoop-aws" % "3.4.2",
  "org.apache.hadoop" % "hadoop-common" % "3.4.2",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.5.23",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  "org.typelevel" %% "log4cats-slf4j" % "2.7.1",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
  // Doobie-scalatest is not available for Scala 3 as of 0.13.4
  
  // Testcontainers for integration tests
  "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.4" % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test
)

// Assembly settings for building a fat JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => xs match {
    case "MANIFEST.MF" :: Nil => MergeStrategy.discard
    case "services" :: _ => MergeStrategy.concat
    case _ => MergeStrategy.discard
  }
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

assembly / mainClass := Some("com.async2databricks.Main")
