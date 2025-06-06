/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.plan.stream.sql

import org.apache.flink.table.api.{TableException, ValidationException}
import org.apache.flink.table.api.config.ExecutionConfigOptions
import org.apache.flink.table.planner.utils.TableTestBase

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{CsvSource, ValueSource}

import java.time.Duration

/** Tests for window table-valued function. */
class WindowTableFunctionTest extends TableTestBase {

  private val util = streamTestUtil()
  util.tableEnv.executeSql(s"""
                              |CREATE TABLE MyTable (
                              |  a INT,
                              |  b BIGINT,
                              |  c STRING,
                              |  d DECIMAL(10, 3),
                              |  rowtime TIMESTAMP(3),
                              |  proctime as PROCTIME(),
                              |  WATERMARK FOR rowtime AS rowtime - INTERVAL '1' SECOND
                              |) with (
                              |  'connector' = 'values'
                              |)
                              |""".stripMargin)

  @Test
  def testTumbleTVF(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testTumbleTVFProctime(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(TABLE MyTable, DESCRIPTOR(proctime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testHopTVF(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        | HOP(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '5' MINUTE, INTERVAL '10' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testHopTVFProctime(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        | HOP(TABLE MyTable, DESCRIPTOR(proctime), INTERVAL '5' MINUTE, INTERVAL '10' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testCumulateTVF(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        | CUMULATE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '10' MINUTE, INTERVAL '1' HOUR))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testCumulateTVFProctime(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        | CUMULATE(TABLE MyTable, DESCRIPTOR(proctime), INTERVAL '10' MINUTE, INTERVAL '1' HOUR))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testWindowOnNonTimeAttribute(): Unit = {
    util.tableEnv.executeSql("""
                               |CREATE VIEW v1 AS
                               |SELECT *, LOCALTIMESTAMP AS cur_time
                               |FROM MyTable
                               |""".stripMargin)
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        | TUMBLE(TABLE v1, DESCRIPTOR(cur_time), INTERVAL '15' MINUTE))
        |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(new ValidationException(
        "The window function requires the timecol is a time attribute type, but is TIMESTAMP(3)."))
  }

  @Test
  def testConflictingFieldNames(): Unit = {
    util.tableEnv.executeSql("""
                               |CREATE VIEW v1 AS
                               |SELECT *, rowtime AS window_start
                               |FROM MyTable
                               |""".stripMargin)
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        | TUMBLE(TABLE v1, DESCRIPTOR(rowtime), INTERVAL '15' MINUTE))
        |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasMessageContaining("Column 'window_start' is ambiguous")
      .isInstanceOf[ValidationException]
  }

  @Test
  def testTumbleTVFWithOffset(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(
        |   TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '15' MINUTE, INTERVAL '5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testTumbleTVFWithNegativeOffset(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(
        |   TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '15' MINUTE, INTERVAL '-5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testTumbleTVFWithNamedParams(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(
        |   DATA => TABLE MyTable,
        |   TIMECOL => DESCRIPTOR(rowtime),
        |   SIZE => INTERVAL '15' MINUTE,
        |   `OFFSET` => INTERVAL '5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testHopTVFWithOffset(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        |  HOP(
        |    TABLE MyTable,
        |    DESCRIPTOR(rowtime),
        |    INTERVAL '1' MINUTE,
        |    INTERVAL '15' MINUTE,
        |    INTERVAL '5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testHopTVFWithNegativeOffset(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        |  HOP(
        |    TABLE MyTable,
        |    DESCRIPTOR(rowtime),
        |    INTERVAL '1' MINUTE,
        |    INTERVAL '15' MINUTE,
        |    INTERVAL '-5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testHopTVFWithNamedParams(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(
        |   DATA => TABLE MyTable,
        |   TIMECOL => DESCRIPTOR(rowtime),
        |   SIZE => INTERVAL '15' MINUTE,
        |   `OFFSET` => INTERVAL '5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testCumulateTVFWithOffset(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        |  CUMULATE(
        |    TABLE MyTable,
        |    DESCRIPTOR(rowtime),
        |    INTERVAL '1' MINUTE,
        |    INTERVAL '15' MINUTE,
        |    INTERVAL '5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testCumulateTVFWithNegativeOffset(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        |  CUMULATE(
        |    TABLE MyTable,
        |    DESCRIPTOR(rowtime),
        |    INTERVAL '1' MINUTE,
        |    INTERVAL '15' MINUTE,
        |    INTERVAL '-5' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testSessionTVF(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(SESSION(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testSessionTVFProctime(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(SESSION(TABLE MyTable, DESCRIPTOR(proctime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testSessionTVFWithPartitionKeys(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(SESSION(TABLE MyTable PARTITION BY (b, a), DESCRIPTOR(rowtime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testSessionTVFWithNamedParams(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        |     SESSION(
        |         DATA => TABLE MyTable PARTITION BY (b, a),
        |         TIMECOL => DESCRIPTOR(rowtime),
        |         GAP => INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testWindowTVFWithNamedParamsOrderChange(): Unit = {
    val sql =
      """
        |SELECT *
        |FROM TABLE(
        |     SESSION(
        |         DATA => TABLE MyTable PARTITION BY (b, a),
        |         GAP => INTERVAL '15' MINUTE,
        |         TIMECOL => DESCRIPTOR(rowtime)))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testProctimeWindowTVFWithMiniBatch(): Unit = {
    enableMiniBatch()
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(TABLE MyTable, DESCRIPTOR(proctime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @Test
  def testRowtimeWindowTVFWithMiniBatch(): Unit = {
    enableMiniBatch()
    val sql =
      """
        |SELECT *
        |FROM TABLE(TUMBLE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '15' MINUTE))
        |""".stripMargin
    util.verifyRelPlan(sql)
  }

  @ParameterizedTest(name = "{index}: {0}")
  @ValueSource(ints = Array[Int](-1, 0))
  def testTumbleWindowWithNonPositiveInterval(size: Int): Unit = {
    val sql =
      s"""
         |SELECT *
         |FROM TABLE(TUMBLE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '$size' MINUTE))
         |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(
        new ValidationException(
          s"TUMBLE table function based aggregate requires size to be positive," +
            s" but got ${size * 1000 * 60} ms."))
  }

  @ParameterizedTest(name = "{index}: {0}, {1}")
  @CsvSource(Array[String]("1, 2", "2, -2"))
  def testTumbleWindowWithWrongOffset(size: Int, offset: Int): Unit = {
    val sql =
      s"""
         |SELECT *
         |FROM TABLE(TUMBLE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '$size' MINUTE, INTERVAL '$offset' MINUTE))
         |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(
        new ValidationException(
          s"TUMBLE table function parameters must satisfy abs(offset) < size, " +
            s"but got size ${size * 60 * 1000} ms and offset ${offset * 60 * 1000} ms."))
  }

  @ParameterizedTest(name = "{index}: {0}, {1}")
  @CsvSource(Array[String]("-1, 1", "0, 2", "3, 0", "4, -3"))
  def testCumulateWindowWithNonPositiveStepAndSize(step: Int, size: Int): Unit = {
    val sql =
      s"""
         |SELECT *
         |FROM TABLE(
         | CUMULATE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '$step' MINUTE, INTERVAL '$size' HOUR))
         |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(
        new ValidationException(
          "CUMULATE table function based aggregate requires maxSize and step to be positive," +
            s" but got maxSize ${size * 1000 * 60 * 60} ms and step ${step * 1000 * 60} ms."))
  }

  @ParameterizedTest(name = "{index}: {0}, {1}")
  @CsvSource(Array[String]("2, 3", "4, 7"))
  def testCumulateWindowWithWrongStepAndSize(step: Int, size: Int): Unit = {
    val sql =
      s"""
         |SELECT *
         |FROM TABLE(
         | CUMULATE(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '$step' SECOND, INTERVAL '$size' SECOND))
         |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(new ValidationException("CUMULATE table function based aggregate requires maxSize must " +
        s"be an integral multiple of step, but got maxSize ${size * 1000} ms and step ${step * 1000} ms."))
  }

  @ParameterizedTest(name = "{index}: {0}, {1}")
  @CsvSource(Array[String]("-1, 1", "0, 2", "3, 0", "4, -3"))
  def testHopWindowWithNonPositiveSlideAndSize(slide: Int, size: Int): Unit = {
    val sql =
      s"""
         |SELECT *
         |FROM TABLE(
         | HOP(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '$slide' MINUTE, INTERVAL '$size' MINUTE))
         |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(
        new ValidationException(
          "HOP table function based aggregate requires slide and size to be positive," +
            s" but got slide ${slide * 1000 * 60} ms and size ${size * 1000 * 60} ms."))
  }

  @ParameterizedTest(name = "{index}: {0}")
  @ValueSource(ints = Array[Int](-1, 0))
  def testSessionWindowWithNonPositiveGap(gap: Int): Unit = {
    val sql =
      s"""
         |SELECT *
         |FROM TABLE(SESSION(TABLE MyTable, DESCRIPTOR(rowtime), INTERVAL '$gap' MINUTE))
         |""".stripMargin

    assertThatThrownBy(() => util.verifyRelPlan(sql))
      .hasCause(
        new ValidationException(
          s"SESSION table function based aggregate requires gap to be positive," +
            s" but got gap ${gap * 1000 * 60} ms."))
  }

  private def enableMiniBatch(): Unit = {
    util.tableConfig.set(
      ExecutionConfigOptions.TABLE_EXEC_MINIBATCH_ENABLED,
      java.lang.Boolean.TRUE)
    util.tableConfig.set(
      ExecutionConfigOptions.TABLE_EXEC_MINIBATCH_SIZE,
      java.lang.Long.valueOf(5L))
    util.tableConfig.set(
      ExecutionConfigOptions.TABLE_EXEC_MINIBATCH_ALLOW_LATENCY,
      Duration.ofSeconds(5L))
  }

}
