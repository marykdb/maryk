package maryk.core.properties.enum

import maryk.checkProtoBufConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.RequestContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class EnumIndexValue(index: UInt, override val name: String) : IndexedEnumImpl<EnumIndexValue>(index)

class EnumIndexBoundsTest {
    private val definition = IndexedEnumDefinition(
        name = "Bounded",
        values = { listOf(EnumIndexValue(1u, "Low"), EnumIndexValue(65535u, "Upper")) },
        unknownCreator = ::EnumIndexValue
    )

    @Test
    fun rejectsDeclarationsWhoseStorageIndexesWouldCollide() {
        for (index in listOf(65536u, 65537u, UInt.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> {
                IndexedEnumDefinition("Invalid", { listOf(EnumIndexValue(1u, "Low"), EnumIndexValue(index, "High")) }).check()
            }
        }
    }

    @Test
    fun supportedBoundaryIndexesKeepTheirStorageAndTransportBytes() {
        for ((index, storage, transport) in listOf(
            Triple(0u, listOf(0.toByte(), 0.toByte()), listOf(0.toByte())),
            Triple(65535u, listOf(255.toByte(), 255.toByte()), listOf(255.toByte(), 255.toByte(), 3.toByte()))
        )) {
            val value = definition.resolve(index)!!
            val storageBytes = mutableListOf<Byte>()
            definition.writeStorageBytes(value, storageBytes::add)
            assertEquals(storage, storageBytes)
            var offset = 0
            assertEquals(value, definition.readStorageBytes(storageBytes.size) { storageBytes[offset++] })

            val transportBytes = mutableListOf<Byte>()
            definition.writeTransportBytes(value, WriteCacheFailer(), transportBytes::add, null)
            assertEquals(transport, transportBytes)
            offset = 0
            assertEquals(value, definition.readTransportBytes(transportBytes.size, { transportBytes[offset++] }, null, null))
        }
    }

    @Test
    fun unknownCasesCannotResolveOutsideTheStorageRange() {
        assertFailsWith<IllegalArgumentException> { definition.resolve(65537u) }
        assertFailsWith<IllegalArgumentException> { definition.resolve("Future(65537)") }
        val bytes = listOf(129.toByte(), 128.toByte(), 4.toByte()) // 65537 as a varint
        var offset = 0
        assertFailsWith<IllegalArgumentException> {
            definition.readTransportBytes(bytes.size, { bytes[offset++] }, null, null)
        }
    }

    @Test
    fun directlyConstructedUnknownCasesFailBeforeWritingAnyBytes() {
        val invalid = EnumIndexValue(65537u, "Future")
        val bytes = mutableListOf<Byte>()
        assertFailsWith<IllegalArgumentException> { definition.writeStorageBytes(invalid, bytes::add) }
        assertEquals(emptyList(), bytes)
        assertFailsWith<IllegalArgumentException> { definition.calculateStorageByteLength(invalid) }
        assertFailsWith<IllegalArgumentException> { definition.writeTransportBytes(invalid, WriteCacheFailer(), bytes::add, null) }
        assertEquals(emptyList(), bytes)
        assertFailsWith<IllegalArgumentException> { definition.calculateTransportByteLength(invalid) }
        assertFailsWith<OutOfRangeException> {
            EnumDefinition(enum = definition).validateWithRef(null, invalid)
        }
    }

    @Test
    fun collectionValidationErrorsDoNotEmbedUnencodableEnumReferences() {
        val invalid = EnumIndexValue(65537u, "Future")
        val enum = EnumDefinition(enum = definition, maxValue = definition.resolve(1u))
        val map = MapDefinition(keyDefinition = enum, valueDefinition = StringDefinition())
        val mapFailure = assertFailsWith<ValidationUmbrellaException> {
            map.validateWithRef(null, mapOf(invalid to "value"))
        }
        checkProtoBufConversion(mapFailure, ValidationUmbrellaException, { RequestContext(emptyMap()) })

        val set = SetDefinition(valueDefinition = enum)
        val setFailure = assertFailsWith<ValidationUmbrellaException> {
            set.validateWithRef(null, setOf(invalid))
        }
        checkProtoBufConversion(setFailure, ValidationUmbrellaException, { RequestContext(emptyMap()) })
    }
}
