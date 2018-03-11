package maryk.core.json.yaml

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.ValueType
import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForValue
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
        """.trimMargin())
        testForValues(reader)
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
        """.trimMargin())
        testForValues(reader)
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
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
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