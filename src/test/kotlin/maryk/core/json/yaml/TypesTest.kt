package maryk.core.json.yaml

import maryk.core.json.ValueType
import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
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
        |- [0b1_001_001, 1_234, 012_345, 0xFF_EEDD, 1_90:20:30]
        """.trimMargin())
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
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}