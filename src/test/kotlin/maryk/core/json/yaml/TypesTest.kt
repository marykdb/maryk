package maryk.core.json.yaml

import maryk.core.extensions.initByteArrayByHex
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.ValueType
import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForByteArrayValue
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForValue
import maryk.core.properties.types.DateTime
import kotlin.test.Test

class TypesTest {
    @Test
    fun read_auto_typed_values() {
        val reader = createMarykYamlReader("""
        |- [yes, Yes, YES, Y, on, ON, On, true, True, TRUE]
        |- [no, No, NO, N, off, OFF, Off, false, False, FALSE]
        |- [~, null, NULL, Null]
        |- [.Nan, .NAN, .nan]
        |- [.Inf, .INF, .inf, +.Inf, +.INF, +.inf, -.Inf, -.INF, -.inf]
        |- [0b1_001_001, 1_234, 012_345, 0xFF_EEDD, 1_90:20:30, -20:30, -1234]
        |- [1.2345, -1.0, 0.0, 2.3e4, -2.2323e-44]
        |- [2018-03-13, 2017-12-01T12:45:13, !!timestamp 2016-09-05 1:12:05.123456789Z, !!timestamp 2015-05-24T12:03:55+05:00, !!timestamp 2014-02-28T09:34:43.22Z]
        """.trimMargin())
        testForValues(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_strong_typed_values() {
        val reader = createMarykYamlReader("""
        |- [!!bool yes, !!bool Yes, !!bool YES, !!bool Y, !!bool on, !!bool ON, !!bool On, !!bool true, !!bool True, !!bool TRUE]
        |- [!!bool no, !!bool No, !!bool NO, !!bool N, !!bool off, !!bool OFF, !!bool Off, !!bool false, !!bool False, !!bool FALSE]
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
        """.trimMargin())
        testForValues(reader)
        testForByteArrayValue(
            reader,
            initByteArrayByHex("4749463839610c000c00840000fffff7f5f5eee9e9e5666666000000e7e7e75e5e5ef3f3ed8e8e8ee0e0e09f9f9f939393a7a7a79e9e9e696969636363a3a3a3848484fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef9fffef921fe0e4d61646520776974682047494d50002c000000000c000c0000052c20208e81309ee34014e86910c4d18a081ccf804d247aefff308570b8b031660d1bce01c3011e102720820a01003b"),
            YamlValueType.Binary
        )
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    private fun testForValues(reader: IsJsonLikeReader) {
        testForArrayStart(reader)
        testForArrayStart(reader)
        (1..10).forEach {
            testForValue(reader, true, ValueType.Bool)
        }
        testForArrayEnd(reader)
        testForArrayStart(reader)
        (1..10).forEach {
            testForValue(reader, false, ValueType.Bool)
        }
        testForArrayEnd(reader)
        testForArrayStart(reader)
        (1..4).forEach {
            testForValue(reader, null, ValueType.Null)
        }
        testForArrayEnd(reader)
        testForArrayStart(reader)
        (1..3).forEach {
            testForValue(reader, Double.NaN, ValueType.Float)
        }
        testForArrayEnd(reader)
        testForArrayStart(reader)
        (1..6).forEach {
            testForValue(reader, Double.POSITIVE_INFINITY, ValueType.Float)
        }
        (1..3).forEach {
            testForValue(reader, Double.NEGATIVE_INFINITY, ValueType.Float)
        }
        testForArrayEnd(reader)
        testForArrayStart(reader)
        testForValue(reader, 0b1001001L, ValueType.Int)
        testForValue(reader, 1234L, ValueType.Int)
        testForValue(reader, 5349L, ValueType.Int)
        testForValue(reader, 16772829L, ValueType.Int)
        testForValue(reader, 685230L, ValueType.Int)
        testForValue(reader, -1170L, ValueType.Int)
        testForValue(reader, -1234L, ValueType.Int)
        testForArrayEnd(reader)
        testForArrayStart(reader)
        testForValue(reader, 1.2345, ValueType.Float)
        testForValue(reader, -1.0, ValueType.Float)
        testForValue(reader, 0.0, ValueType.Float)
        testForValue(reader, 2.3e4, ValueType.Float)
        testForValue(reader, -2.2323e-44, ValueType.Float)
        testForArrayEnd(reader)
        testForArrayStart(reader)
        testForValue(reader, DateTime(2018, 3, 13), YamlValueType.TimeStamp)
        testForValue(reader, DateTime(2017, 12, 1, 12, 45, 13), YamlValueType.TimeStamp)
        testForValue(reader, DateTime(2016, 9, 5, 1, 12, 5, 123), YamlValueType.TimeStamp)
        testForValue(reader, DateTime(2015, 5, 24, 7, 3, 55), YamlValueType.TimeStamp)
        testForValue(reader, DateTime(2014, 2, 28, 9, 34, 43, 220), YamlValueType.TimeStamp)
        testForArrayEnd(reader)
    }

    @Test
    fun read_wrong_typed_values() {
        testForInvalidYaml(
            createMarykYamlReader("!!null yes")
        )

        testForInvalidYaml(
            createMarykYamlReader("!!bool ~")
        )

        testForInvalidYaml(
            createMarykYamlReader("!!float wrong")
        )
    }

    @Test
    fun read_string_typed_values() {
        testForValue(
            createMarykYamlReader("!!str .NAN"),
            ".NAN",
            ValueType.String
        )

        testForValue(
            createMarykYamlReader("!!str 1.2345"),
            "1.2345",
            ValueType.String
        )

        testForValue(
            createMarykYamlReader("!!str true"),
            "true",
            ValueType.String
        )
    }
}