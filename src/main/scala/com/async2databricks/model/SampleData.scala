package com.async2databricks.model

import doobie.postgres.implicits.JavaLocalDateTimeMeta
import doobie.util.Read
import doobie.util.Write
import java.time.LocalDateTime

/** Sample data model representing a row from the database This is a generic
  * example - adjust fields based on your actual schema
  */
case class SampleData(
    id: Long,
    name: String,
    value: Double,
    category: String,
    createdAt: LocalDateTime
) derives Read,
      Write
