package com.async2databricks.model

import java.time.LocalDateTime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SampleDataSpec extends AnyFlatSpec with Matchers {

  "SampleData" should "create an instance with valid values" in {
    val now = LocalDateTime.now()
    val data = SampleData(
      id = 1L,
      name = "Test Product",
      value = 99.99,
      category = "Test Category",
      createdAt = now
    )

    data.id shouldBe 1L
    data.name shouldBe "Test Product"
    data.value shouldBe 99.99
    data.category shouldBe "Test Category"
    data.createdAt shouldBe now
  }

  it should "support case class operations" in {
    val now = LocalDateTime.now()
    val data1 = SampleData(1L, "Product", 100.0, "Cat", now)
    val data2 = data1.copy(name = "Updated Product")

    data2.name shouldBe "Updated Product"
    data2.id shouldBe data1.id
  }
}
