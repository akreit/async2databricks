package com.async2databricks.utils

import cats.effect.Sync
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jFactory

/** Mixin trait to provide a Cats Effect logger. Helpful to add asynchronous
  * logging capabilities.
  * @see
  *   https://typelevel.org/log4cats/#logging-using-capabilities
  */
trait CatsLogger {
  def logger[F[_]: Sync]: SelfAwareStructuredLogger[F] =
    Slf4jFactory.create[F].getLogger
}
