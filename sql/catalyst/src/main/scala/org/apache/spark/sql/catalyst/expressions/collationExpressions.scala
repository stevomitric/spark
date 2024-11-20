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

package org.apache.spark.sql.catalyst.expressions

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.analysis.{ExpressionBuilder, UnresolvedException}
import org.apache.spark.sql.catalyst.expressions.codegen._
import org.apache.spark.sql.catalyst.util.{AttributeNameParser, CollationFactory}
import org.apache.spark.sql.errors.QueryCompilationErrors
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.internal.types.StringTypeWithCollation
import org.apache.spark.sql.types._

// scalastyle:off line.contains.tab
@ExpressionDescription(
  usage = "_FUNC_(expr, collationName) - Marks a given expression with the specified collation.",
  arguments = """
    Arguments:
      * expr - String expression to perform collation on.
      * collationName - Foldable string expression that specifies the collation name.
  """,
  examples = """
    Examples:
      > SELECT COLLATION('Spark SQL' _FUNC_ UTF8_LCASE);
      UTF8_LCASE
  """,
  since = "4.0.0",
  group = "string_funcs")
// scalastyle:on line.contains.tab
object CollateExpressionBuilder extends ExpressionBuilder {
  override def build(funcName: String, expressions: Seq[Expression]): Expression = {
    expressions match {
      case Seq(e: Expression, collationExpr: Expression) =>
        (collationExpr.dataType, collationExpr.foldable) match {
          case (_: StringType, true) =>
            val evalCollation = collationExpr.eval()
            if (evalCollation == null) {
              throw QueryCompilationErrors.unexpectedNullError("collation", collationExpr)
            } else {
              if (!SQLConf.get.trimCollationEnabled &&
                evalCollation.toString.toUpperCase().contains("TRIM")) {
                throw QueryCompilationErrors.trimCollationNotEnabledError()
              }
              Collate(e, scala.Left(UnresolvedCollation(
                AttributeNameParser.parseAttributeName(evalCollation.toString))))
            }
          case (_: StringType, false) => throw QueryCompilationErrors.nonFoldableArgumentError(
            funcName, "collationName", StringType)
          case (_, _) => throw QueryCompilationErrors.unexpectedInputDataTypeError(
            funcName, 1, StringType, collationExpr)
        }
      case s => throw QueryCompilationErrors.wrongNumArgsError(funcName, Seq(2), s.length)
    }
  }
}

/**
 * An expression that marks a given expression with specified collation.
 * This function is pass-through, it will not modify the input data.
 * Only type metadata will be updated.
 */
case class Collate(child: Expression, collation: Either[UnresolvedCollation, ResolvedCollation])
  extends UnaryExpression with ExpectsInputTypes {
  override def dataType: DataType = collation.merge.dataType
  override def inputTypes: Seq[AbstractDataType] =
    Seq(StringTypeWithCollation(supportsTrimCollation = true))

  override protected def withNewChildInternal(
    newChild: Expression): Expression = copy(newChild)

  override def eval(row: InternalRow): Any = child.eval(row)

  override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode =
    defineCodeGen(ctx, ev, (in) => in)

  override def sql: String = s"$prettyName(${child.sql}, $collation)"

  override def toString: String = s"$prettyName($child, $collation)"
}

/**
 * An expression that marks an unresolved collation name.
 *
 * This class is used to represent a collation name that has not yet been resolved from a fully
 * qualified collation name. It is used during the analysis phase, where the collation name is
 * specified but not yet validated or resolved.
 */
case class UnresolvedCollation(collationName: Seq[String])
  extends LeafExpression with Unevaluable {
  override def dataType: DataType = throw new UnresolvedException("dataType")

  override def nullable: Boolean = false
}

/**
 * An expression that represents a resolved collation name.
 */
case class ResolvedCollation(collationName: String) extends LeafExpression with CodegenFallback {
  override def nullable: Boolean = false

  override def eval(input: InternalRow): Any = {}

  override def dataType: DataType = StringType(CollationFactory.collationNameToId(collationName))
}

// scalastyle:off line.contains.tab
@ExpressionDescription(
  usage = "_FUNC_(expr) - Returns the collation name of a given expression.",
  arguments = """
    Arguments:
      * expr - String expression to perform collation on.
  """,
  examples = """
    Examples:
      > SELECT _FUNC_('Spark SQL');
      UTF8_BINARY
  """,
  since = "4.0.0",
  group = "string_funcs")
// scalastyle:on line.contains.tab
case class Collation(child: Expression)
  extends UnaryExpression with RuntimeReplaceable with ExpectsInputTypes {
  override protected def withNewChildInternal(newChild: Expression): Collation = copy(newChild)
  override lazy val replacement: Expression = {
    val collationId = child.dataType.asInstanceOf[StringType].collationId
    val fullyQualifiedCollationName = CollationFactory.fullyQualifiedName(collationId)
    Literal.create(fullyQualifiedCollationName, SQLConf.get.defaultStringType)
  }
  override def inputTypes: Seq[AbstractDataType] =
    Seq(StringTypeWithCollation(supportsTrimCollation = true))
}
