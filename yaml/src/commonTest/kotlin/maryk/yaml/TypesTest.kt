package maryk.yaml

import kotlinx.datetime.LocalDateTime
import maryk.json.IsJsonLikeReader
import maryk.json.ValueType
import maryk.yaml.YamlValueType.Binary
import maryk.yaml.YamlValueType.TimeStamp
import kotlin.test.Test

class TypesTest {
    @Test
    fun readAutoTypedValues() {
        createYamlReader("""
        |- [true, True, TRUE]
        |- [false, False, FALSE]
        |- [yes, Yes, YES, Y, on, ON, On, no, No, NO, N, off, OFF, Off]
        |- [~, null, NULL, Null]
        |- [.Nan, .NAN, .nan]
        |- [.Inf, .INF, .inf, +.Inf, +.INF, +.inf, -.Inf, -.INF, -.inf]
        |- [0b1_001_001, 1_234, 012_345, 0xFF_EEDD, 1_90:20:30, -20:30, -1234]
        |- [1.2345, -1.0, 0.0, 2.3e4, -2.2323e-44]
        |- [2018-03-13, 2017-12-01T12:45:13, !!timestamp 2016-09-05 1:12:05.123456789Z, !!timestamp 2015-05-24T12:03:55+05:00, !!timestamp 2014-02-28T09:34:43.22Z]
        """.trimMargin()).apply {
            testForValues()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readStrongTypedValues() {
        createYamlReader("""
        |- [!!bool true, !!bool True, !!bool TRUE]
        |- [!!bool false, !!bool False, !!bool FALSE]
        |- [!!str yes, !!str Yes, !!str YES, !!str Y, !!str on, !!str ON, !!str On, !!str no, !!str No, !!str NO, !!str N, !!str off, !!str OFF, !!str Off]
        |- [!!null ~, !!null null, !!null NULL, !!null Null]
        |- [!!float .Nan, !!float .NAN, !!float .nan]
        |- [!!float .Inf, !!float .INF, !!float .inf, !!float +.Inf, !!float +.INF, !!float +.inf, !!float -.Inf, !!float -.INF, !!float -.inf]
        |- [!!int 0b1_001_001, !!int 1_234, !!int 012_345, !!int 0xFF_EEDD, !!int 1_90:20:30, !!int -20:30, !!int -1234]
        |- [!!float 1.2345, !!float -1.0, !!float 0.0, !!float 2.3e4, !!float -2.2323e-44]
        |- [!!timestamp 2018-03-13, !!timestamp 2017-12-01T12:45:13Z, !!timestamp 2016-09-05 1:12:05.123456789Z, 2015-05-24T12:03:55+05:00, 2014-02-28T09:34:43.22Z]
        |- !!binary "\
        |R0lGODlhDAAMAIQAAP//9/X17unp5WZmZgAAAOfn515eXvPz7Y6OjuDg4J+fn5\
        |OTk6enp56enmlpaWNjY6Ojo4SEhP/++f/++f/++f/++f/++f/++f/++f/++f/+\
        |+f/++f/++f/++f/++f/++SH+Dk1hZGUgd2l0aCBHSU1QACwAAAAADAAMAAAFLC\
        |AgjoEwnuNAFOhpEMTRiggcz4BNJHrv/zCFcLiwMWYNG84BwwEeECcgggoBADs="
        """.trimMargin()).apply {
            testForValues()
            assertByteArrayValue(
                "4749463839610c000c00840000fffff7f5f5eee9e9e5666666000000e7e7e75e5e5ef3f3ed8e8e8ee0e0e09f9f9f939393a7a7a79e9e9e696969636363a3a3a3848484fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef921fe0e4d61646520776974682047494d50002c000000000c000c0000052c20208e81309ee34014e86910c4d18a081ccf804d247aefff308570b8b031660d1bce01c3011e102720820a01003b".hexToByteArray(),
                Binary
            )
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readWrongTypedValues() {
        createYamlReader("!!null yes").apply {
            assertInvalidYaml()
        }

        createYamlReader("!!bool ~").apply {
            assertInvalidYaml()
        }

        createYamlReader("!!float wrong").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readStringTypedValues() {
        createYamlReader("!!str .NAN").apply {
            assertValue(
                ".NAN",
                ValueType.String
            )
        }

        createYamlReader("!!str 1.2345").apply {
            assertValue(
                "1.2345",
                ValueType.String
            )
        }

        createYamlReader("!!str true").apply {
            assertValue(
                "true",
                ValueType.String
            )
        }
    }

    private fun IsJsonLikeReader.testForValues() {
        assertStartArray()
        assertStartArray()
        listOf("true", "True", "TRUE").forEach {
            assertValue(true, ValueType.Bool)
        }
        assertEndArray()
        assertStartArray()
        listOf("false", "False", "FALSE").forEach {
            assertValue(false, ValueType.Bool)
        }
        assertEndArray()
        assertStartArray()
        listOf("yes", "Yes", "YES", "Y", "on", "ON", "On", "no", "No", "NO", "N", "off", "OFF", "Off").forEach {
            assertValue(it, ValueType.String)
        }
        assertEndArray()
        assertStartArray()
        repeat(4) {
            assertValue(null, ValueType.Null)
        }
        assertEndArray()
        assertStartArray()
        repeat(3) {
            assertValue(Double.NaN, ValueType.Float)
        }
        assertEndArray()
        assertStartArray()
        repeat(6) {
            assertValue(Double.POSITIVE_INFINITY, ValueType.Float)
        }
        repeat(3) {
            assertValue(Double.NEGATIVE_INFINITY, ValueType.Float)
        }
        assertEndArray()
        assertStartArray()
        assertValue(0b1001001L, ValueType.Int)
        assertValue(1234L, ValueType.Int)
        assertValue(5349L, ValueType.Int)
        assertValue(16772829L, ValueType.Int)
        assertValue(685230L, ValueType.Int)
        assertValue(-1170L, ValueType.Int)
        assertValue(-1234L, ValueType.Int)
        assertEndArray()
        assertStartArray()
        assertValue(1.2345, ValueType.Float)
        assertValue(-1.0, ValueType.Float)
        assertValue(0.0, ValueType.Float)
        assertValue(2.3e4, ValueType.Float)
        assertValue(-2.2323e-44, ValueType.Float)
        assertEndArray()
        assertStartArray()
        assertValue(LocalDateTime(2018, 3, 13, 0, 0), TimeStamp)
        assertValue(LocalDateTime(2017, 12, 1, 12, 45, 13), TimeStamp)
        assertValue(LocalDateTime(2016, 9, 5, 1, 12, 5, 123000000), TimeStamp)
        assertValue(LocalDateTime(2015, 5, 24, 7, 3, 55), TimeStamp)
        assertValue(LocalDateTime(2014, 2, 28, 9, 34, 43, 220000000), TimeStamp)
        assertEndArray()
    }
}
