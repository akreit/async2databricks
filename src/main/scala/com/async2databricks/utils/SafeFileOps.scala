package com.async2databricks.utils

import cats.effect.IO
import cats.effect.kernel.Resource
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import scala.io.BufferedSource
import scala.io.Source
import scala.reflect.ClassTag

/** Utility object for safe file operations using Cats Effect.
  */
object SafeFileOps {

  private def getSource(fileName: String): IO[BufferedSource] = IO.blocking(
    Source.fromResource(fileName)
  )

  private def closeSource(src: Source): IO[Unit] = IO.blocking {
    src.close()
  }

  /** Create a Resource for a BufferedSource from a resource file. Ensures that
    * the source is properly closed after use.
    *
    * @return
    *   Resource[IO, BufferedSource]
    */
  val resource: String => Resource[IO, BufferedSource] = (fileName: String) =>
    Resource.make(getSource(fileName))(closeSource)

  /** Read the entire content of a Source into a single trimmed String. This
    * operation is performed in a blocking context.
    *
    * @param src
    *   The Source to read from
    * @return
    *   IO[String] containing the file content
    */
  def readFile(src: Source): IO[String] = IO.blocking {
    src
      .getLines()
      .map(_.trim)
      .mkString(" ")
  }

  /** Load configuration from a resource file using PureConfig. Return an
    * effectful IO[A] containing the configuration object.
    *
    * @param configResource
    *   The resource file path
    * @tparam A
    *   The type of the configuration object
    * @return
    *   IO[A] containing the loaded configuration
    */
  def loadConfig[A: {ConfigReader, ClassTag}](configResource: String): IO[A] = {
    ConfigSource.resources(configResource).loadF[IO, A]()
  }
}
