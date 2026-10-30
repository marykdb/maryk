package maryk.core.processors.datastore

import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IncrementingMapStorageAllocationTest {
    @Test
    fun everyIntegerDescriptorPreservesItsEstablishedFirstAllocatedKeys() {
        checkInteger(SInt8, (Byte.MIN_VALUE + 1).toByte(), (Byte.MIN_VALUE + 2).toByte())
        checkInteger(SInt16, (Short.MIN_VALUE + 1).toShort(), (Short.MIN_VALUE + 2).toShort())
        checkInteger(SInt32, Int.MIN_VALUE + 1, Int.MIN_VALUE + 2)
        checkInteger(SInt64, Long.MIN_VALUE + 1, Long.MIN_VALUE + 2)
        checkInteger(UInt8, 1u.toUByte(), 2u.toUByte())
        checkInteger(UInt16, 1u.toUShort(), 2u.toUShort())
        checkInteger(UInt32, 1u, 2u)
        checkInteger(UInt64, 1uL, 2uL)
    }

    @Test
    fun floatingPointDescriptorsRejectAllocationBeforeWritingButAcceptExplicitKeys() {
        checkFloating(Float32, 1f)
        checkFloating(Float64, 1.0)
    }

    private fun <K : Comparable<K>> checkInteger(descriptor: NumberDescriptor<K>, first: K, second: K) {
        val definition = IncrementingMapDefinition(keyNumberDescriptor = descriptor, valueDefinition = StringDefinition())
        var writes = 0
        val writer: ValueWriter<IsPropertyDefinition<*>> = { _, _, _, _ -> writes++ }
        val keys = writeIncMapAdditionsToStorage(ByteArray(descriptor.size) { -1 }, writer, definition, listOf("first", "second"))
        assertEquals(listOf(first, second), keys, descriptor.type.name)
        assertEquals(2, writes)
    }

    private fun <K : Comparable<K>> checkFloating(descriptor: NumberDescriptor<K>, explicit: K) {
        val definition = IncrementingMapDefinition(keyNumberDescriptor = descriptor, valueDefinition = StringDefinition())
        var writes = 0
        val seed = ByteArray(descriptor.size) { -1 }
        val writer: ValueWriter<IsPropertyDefinition<*>> = { _, _, _, _ -> writes++ }
        assertFailsWith<RequestException> {
            writeIncMapAdditionsToStorage(seed, writer, definition, listOf("value"))
        }
        assertEquals(0, writes)
        assertEquals(emptyList(), writeIncMapAdditionsToStorage(seed, writer, definition, emptyList()))
        writeMapToStorage(0, null, writer, definition, mapOf(explicit to "value"))
        assertEquals(2, writes)
    }
}
