package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredValuePresenceTest {
    @Test
    fun canonicalZeroCountAndPropertyDeletionHaveDistinctIdentities() {
        val definition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
        for (count in listOf(0, 1, 127, 128, 16_384)) {
            val bytes = count.toVarBytes()
            assertEquals(count, readStoredCount(bytes))
            assertTrue(hasStoredValuePayload(definition, bytes, 0, bytes.size))
        }
        assertNull(readStoredCount(StoredPropertyDeleteMarker))
        assertFalse(hasStoredValuePayload(definition, StoredPropertyDeleteMarker, 0, 2))
        assertFailsWith<StorageException> { readStoredCount(byteArrayOf(1, 0)) }
        assertFailsWith<StorageException> { readStoredCount(byteArrayOf(0x80.toByte(), 0)) }
        val copy = StoredPropertyDeleteMarker
        copy[0] = 1
        assertTrue(isStoredPropertyDeleteMarker(StoredPropertyDeleteMarker))
    }

    @Test
    fun scalarNoOpIdentityUsesTheSchemaFloatingPointWidth() {
        val double = NumberDefinition(type = Float64)
        val float = NumberDefinition(type = Float32)
        assertTrue(areSameStoredScalarValues(double, 1.5, 1.5))
        assertFalse(areSameStoredScalarValues(double, Double.fromBits(0x7ff8000000000001L), Double.fromBits(0x7ff8000000000002L)))
        assertTrue(areSameStoredScalarValues(double, Double.fromBits(0x7ff8000000000001L), Double.fromBits(0x7ff8000000000001L)))
        assertFalse(areSameStoredScalarValues(float, Float.fromBits(0x7fc00001), Float.fromBits(0x7fc00002)))
        assertTrue(areSameStoredScalarValues(float, Float.fromBits(0x7fc00001), Float.fromBits(0x7fc00001)))
    }
}
