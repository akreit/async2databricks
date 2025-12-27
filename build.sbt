name := "async2databricks"

version := "0.1.0"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  // Doobie for database access
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC4",
  
  // Parquet4s for Parquet file handling
  "com.github.mjakubowski84" %% "parquet4s-core" % "2.15.0",
  "com.github.mjakubowski84" %% "parquet4s-fs2" % "2.15.0",
  
  // PureConfig for configuration
  "com.github.pureconfig" %% "pureconfig" % "0.17.4",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.4",
  
  // Cats Effect
  "org.typelevel" %% "cats-effect" % "3.5.2",
  
  // FS2
  "co.fs2" %% "fs2-core" % "3.9.3",
  "co.fs2" %% "fs2-io" % "3.9.3",
  
  // AWS S3 SDK
  "software.amazon.awssdk" % "s3" % "2.21.26",
  
  // Hadoop for S3A filesystem
  "org.apache.hadoop" % "hadoop-aws" % "3.3.4",
  "org.apache.hadoop" % "hadoop-common" % "3.3.4",
  
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  
  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
  "org.tpolecat" %% "doobie-scalatest" % "1.0.0-RC4" % Test
)

// Compiler options
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
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
