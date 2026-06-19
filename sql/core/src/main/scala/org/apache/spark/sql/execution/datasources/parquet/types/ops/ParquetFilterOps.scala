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

import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName

/**
 * Optional Parquet filter-pushdown support for a Types Framework type.
 *
 * A framework type that wants its predicates pushed down to Parquet returns a
 * [[ParquetFilterOps]] from [[ParquetTypeOps.parquetFilterOps]]; types that don't support
 * pushdown leave the default (None) and are read without filtering.
 *
 * Dispatch is keyed off the Parquet file's column encoding, not the requested Spark type,
 * because filter pushdown matches the on-disk schema. The ops therefore declares the
 * Parquet primitive + logical annotation it owns ([[primitiveTypeName]] /
 * [[logicalTypeAnnotation]]); `ParquetFilters` reverse-looks-up the ops for a field via
 * [[ParquetTypeOps.filterOpsFor]] and routes that field's predicates here. This keeps the
 * per-type filter knowledge (value conversion, predicate construction) with the type
 * instead of scattered across `ParquetFilters`.
 *
 * Implementations build the parquet-mr [[FilterPredicate]] for each comparison directly
 * (e.g. via `FilterApi.eq(longColumn(path), ...)`), so they own the choice of physical
 * column and the external-value -> physical-value conversion. The eq/notEq/in builders
 * must tolerate a null `value` (used for IsNull / IsNotNull); the ordered builders
 * (lt/ltEq/gt/gtEq) are only invoked with non-null values.
 *
 * @see TimeTypeParquetOps.filterOps for a reference implementation (INT64-backed TimeType)
 * @since 4.3.0
 */
private[parquet] trait ParquetFilterOps {

  /** The Parquet logical type annotation of the column this ops handles (may be null). */
  def logicalTypeAnnotation: LogicalTypeAnnotation

  /** The Parquet primitive type of the column this ops handles. */
  def primitiveTypeName: PrimitiveTypeName

  /** Whether `value` (a non-null external filter value) is pushable for this type. */
  def acceptsValue(value: Any): Boolean

  def makeEq(columnPath: Array[String], value: Any): FilterPredicate
  def makeNotEq(columnPath: Array[String], value: Any): FilterPredicate
  def makeLt(columnPath: Array[String], value: Any): FilterPredicate
  def makeLtEq(columnPath: Array[String], value: Any): FilterPredicate
  def makeGt(columnPath: Array[String], value: Any): FilterPredicate
  def makeGtEq(columnPath: Array[String], value: Any): FilterPredicate
  def makeIn(columnPath: Array[String], values: Array[Any]): FilterPredicate
}
