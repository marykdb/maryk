package maryk.generator.proto3

import maryk.test.models.MarykTypeEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val generatedProto3ForMarykEnum = """
enum MarykTypeEnum {
  UNKNOWN_MARYKTYPEENUM = 0;
  T1 = 1;
  T2 = 2;
  T3 = 3;
  T4 = 4;
  T5 = 5;
  T6 = 6;
  T7 = 7;
}
""".trimIndent()

class GenerateProto3ForEnumTest {
    @Test
    fun generateProto3SchemaForEnum() {
        val output = buildString {
            MarykTypeEnum.generateProto3Schema {
                append(it)
            }
        }

        assertEquals(generatedProto3ForMarykEnum, output)
    }

    @Test
    fun rejectsEnumNamesThatAreInvalidProtoIdentifiers() {
        val exception = assertFailsWith<IllegalArgumentException> {
            buildString {
                `invalid-enum`.generateProto3Schema(::append)
            }
        }

        assertEquals("Proto3 identifier is invalid: invalid-enum", exception.message)
    }

    @Test
    fun rejectsEnumIndexesOutsideTheProtoInt32Range() {
        val exception = assertFailsWith<IllegalArgumentException> {
            buildString {
                OutOfRangeIndex.generateProto3Schema(::append)
            }
        }

        assertEquals("Proto3 enum index must fit in Int32: 2147483648", exception.message)
    }

    @Test
    fun rejectsEnumIndexZeroReservedForTheGeneratedUnknownValue() {
        val exception = assertFailsWith<IllegalArgumentException> {
            buildString {
                ZeroIndex.generateProto3Schema(::append)
            }
        }

        assertEquals("Proto3 enum index must be greater than zero: 0", exception.message)
    }
}

private sealed class `invalid-enum`(index: UInt) : IndexedEnumImpl<`invalid-enum`>(index) {
    object Value : `invalid-enum`(1u)

    companion object : IndexedEnumDefinition<`invalid-enum`>(
        `invalid-enum`::class,
        values = { listOf(Value) },
    )
}

private sealed class OutOfRangeIndex(index: UInt) : IndexedEnumImpl<OutOfRangeIndex>(index) {
    object Value : OutOfRangeIndex(2_147_483_648u)

    companion object : IndexedEnumDefinition<OutOfRangeIndex>(
        OutOfRangeIndex::class,
        values = { listOf(Value) },
    )
}

private sealed class ZeroIndex(index: UInt) : IndexedEnumImpl<ZeroIndex>(index) {
    object Value : ZeroIndex(0u)

    companion object : IndexedEnumDefinition<ZeroIndex>(
        ZeroIndex::class,
        values = { listOf(Value) },
    )
}
