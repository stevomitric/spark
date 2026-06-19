/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.parquet.types.ops

import java.time.LocalTime

import org.apache.parquet.schema.{LogicalTypeAnnotation, Type, Types}
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.{INT32, INT64}
import org.apache.parquet.schema.Type.Repetition.REQUIRED

import org.apache.spark.{SparkFunSuite, SparkRuntimeException}
import org.apache.spark.sql.types.TimeType

/**
 * Unit tests for [[TimeTypeParquetOps.requireCompatibleParquetType]].
 *
 * TimeType is stored in Parquet as INT64 TIME(MICROS, isAdjustedToUTC=false).
 * The read-path guard accepts only that canonical encoding and rejects every
 * other primitive/annotation combination so that reading fails loudly rather
 * than silently mis-decoding (e.g. interpreting NANOS as MICROS, which would
 * be off by 1000x).
 *
 * Note: rejecting isAdjustedToUTC=true is stricter than the legacy
 * ParquetRowConverter guard, which accepts that encoding. This is a known,
 * intentional divergence between the framework and legacy paths for this
 * single case; reconciling it (either by relaxing the framework guard or
 * tightening the legacy one) is tracked by SPARK-57416.
 */
class TimeTypeParquetOpsSuite extends SparkFunSuite {

  private val timeMicros = TimeType(TimeType.MICROS_PRECISION)

  // ---------- accept ----------

  test("accepts INT64 TIME(MICROS, isAdjustedToUTC=false) - the canonical encoding") {
    val field = Types.primitive(INT64, REQUIRED)
      .as(LogicalTypeAnnotation.timeType(false, TimeUnit.MICROS))
      .named("c")
    // Must not throw.
    TimeTypeParquetOps.requireCompatibleParquetType(timeMicros, field)
  }

  // ---------- the four primary reject paths ----------

  test("rejects raw INT64 with no logical type annotation") {
    val field = Types.primitive(INT64, REQUIRED).named("c")
    assertRejects(timeMicros, field)
  }

  test("rejects INT64 TIME(NANOS, isAdjustedToUTC=false)") {
    val field = Types.primitive(INT64, REQUIRED)
      .as(LogicalTypeAnnotation.timeType(false, TimeUnit.NANOS))
      .named("c")
    assertRejects(timeMicros, field)
  }

  test("rejects INT32 TIME(MILLIS, isAdjustedToUTC=false)") {
    // Per Parquet spec TIME(MILLIS) is INT32; the primitive-type guard catches it.
    val field = Types.primitive(INT32, REQUIRED)
      .as(LogicalTypeAnnotation.timeType(false, TimeUnit.MILLIS))
      .named("c")
    assertRejects(timeMicros, field)
  }

  test("rejects INT64 TIME(MICROS, isAdjustedToUTC=true)") {
    // The intended framework behavior is to reject this encoding: the canonical
    // TimeType representation is local-time (isAdjustedToUTC=false). The legacy
    // ParquetRowConverter guard accepts the encoding, so this is a known,
    // intentional framework-vs-legacy divergence; reconciliation is tracked by
    // SPARK-57416.
    val field = Types.primitive(INT64, REQUIRED)
      .as(LogicalTypeAnnotation.timeType(true, TimeUnit.MICROS))
      .named("c")
    assertRejects(timeMicros, field)
  }

  // ---------- additional rejects for full reject-set coverage ----------

  test("rejects INT64 TIMESTAMP(MICROS) - wrong annotation kind") {
    val field = Types.primitive(INT64, REQUIRED)
      .as(LogicalTypeAnnotation.timestampType(false, TimeUnit.MICROS))
      .named("c")
    assertRejects(timeMicros, field)
  }

  test("rejects INT64 DECIMAL - wrong annotation kind") {
    val field = Types.primitive(INT64, REQUIRED)
      .as(LogicalTypeAnnotation.decimalType(2, 18))
      .named("c")
    assertRejects(timeMicros, field)
  }

  test("rejects non-primitive (group) type") {
    val field: Type = Types.buildGroup(REQUIRED).named("c")
    assertRejects(timeMicros, field)
  }

  // Note: a "BINARY with TIME(MICROS) annotation" combination is impossible to
  // construct - the parquet-mr Types builder itself rejects it with
  // IllegalStateException("TIME(MICROS,false) can only annotate INT64"). So the
  // wrong-primitive branch of requireCompatibleParquetType is unreachable for
  // the TIME annotation; the raw-INT64 / TIMESTAMP / DECIMAL / group tests
  // above already exercise the !isPrimitive and "non-TIME annotation" branches.

  // ---------- filter pushdown ops ----------

  test("filterOps accepts LocalTime values and rejects others") {
    val ops = TimeTypeParquetOps.filterOps
    assert(ops.acceptsValue(LocalTime.of(1, 2, 3)))
    assert(!ops.acceptsValue(java.lang.Long.valueOf(1L)))
    assert(!ops.acceptsValue("12:00:00"))
  }

  test("filterOps declares the canonical TimeType Parquet encoding") {
    val ops = TimeTypeParquetOps.filterOps
    assert(ops.primitiveTypeName === INT64)
    assert(ops.logicalTypeAnnotation ===
      LogicalTypeAnnotation.timeType(false, TimeUnit.MICROS))
  }

  test("filterOps builds predicates for LocalTime, including null for eq/notEq/in") {
    val ops = TimeTypeParquetOps.filterOps
    val path = Array("c")
    val t = LocalTime.of(23, 59, 59, 123456000)
    assert(ops.makeEq(path, t) != null)
    assert(ops.makeEq(path, null) != null)
    assert(ops.makeNotEq(path, null) != null)
    assert(ops.makeLt(path, t) != null)
    assert(ops.makeLtEq(path, t) != null)
    assert(ops.makeGt(path, t) != null)
    assert(ops.makeGtEq(path, t) != null)
    assert(ops.makeIn(path, Array[Any](t, null)) != null)
  }

  test("ParquetTypeOps.filterOpsFor resolves the TimeType encoding and nothing else") {
    assert(ParquetTypeOps.filterOpsFor(
      LogicalTypeAnnotation.timeType(false, TimeUnit.MICROS), INT64).isDefined)
    // A different unit, isAdjustedToUTC=true, primitive, or annotation kind is not the
    // TimeType encoding, so no framework filter ops is returned (pushdown falls through).
    assert(ParquetTypeOps.filterOpsFor(
      LogicalTypeAnnotation.timeType(false, TimeUnit.NANOS), INT64).isEmpty)
    assert(ParquetTypeOps.filterOpsFor(
      LogicalTypeAnnotation.timeType(true, TimeUnit.MICROS), INT64).isEmpty)
    assert(ParquetTypeOps.filterOpsFor(
      LogicalTypeAnnotation.timeType(false, TimeUnit.MICROS), INT32).isEmpty)
    assert(ParquetTypeOps.filterOpsFor(null, INT64).isEmpty)
  }

  // ---------- helper ----------

  private def assertRejects(sparkType: TimeType, field: Type): Unit = {
    val ex = intercept[SparkRuntimeException] {
      TimeTypeParquetOps.requireCompatibleParquetType(sparkType, field)
    }
    assert(ex.getCondition === "PARQUET_CONVERSION_FAILURE.UNSUPPORTED",
      s"expected PARQUET_CONVERSION_FAILURE.UNSUPPORTED, got ${ex.getCondition}")
  }
}
