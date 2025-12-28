package com.async2databricks.utils

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jFactory

/** Mixin trait to provide a Cats Effect logger. Helpful to add asynchronous
  * logging capabilities.
  */
trait CatsLogger {
  val logger: SelfAwareStructuredLogger[IO] = Slf4jFactory.create[IO].getLogger
}
