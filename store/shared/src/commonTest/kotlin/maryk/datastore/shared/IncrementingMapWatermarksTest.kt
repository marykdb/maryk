package maryk.datastore.shared

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.string
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object WatermarkRecord : RootDataModel<WatermarkRecord>() {
    val scalar by string(1u)
    val wideScalar by string(16u)
    val entries by incrementingMap(3u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

class IncrementingMapWatermarksTest {
    @Test
    fun rootFieldsHaveNoAllocationKeysAndMapEntriesRetainTheirMaximum() {
        assertTrue(incrementingMapKeysForQualifier(WatermarkRecord, WatermarkRecord.scalar.ref().toStorageByteArray()).isEmpty())
        assertTrue(incrementingMapKeysForQualifier(WatermarkRecord, WatermarkRecord.wideScalar.ref().toStorageByteArray()).isEmpty())
        assertTrue(incrementingMapKeysForQualifier(WatermarkRecord, WatermarkRecord.entries.ref().toStorageByteArray()).isEmpty())
        val item = WatermarkRecord.ref { entries at 7u }
        val maximum = incrementingMapKeysForQualifier(WatermarkRecord, item.toStorageByteArray()).single()
        assertEquals<Any>(7u, maximum.key)
        assertEquals<Any>(WatermarkRecord.entries.ref(), maximum.reference)
    }

    @Test
    fun malformedScalarQualifiersStillRejectInvalidTypesAndPaths() {
        assertFailsWith<DefNotFoundException> { incrementingMapKeysForQualifier(WatermarkRecord, byteArrayOf(0x71)) }
        assertFailsWith<ParseException> { incrementingMapKeysForQualifier(WatermarkRecord, byteArrayOf(0x0f)) }
        assertFailsWith<ParseException> { incrementingMapKeysForQualifier(WatermarkRecord, byteArrayOf(0x89.toByte())) }
        assertFailsWith<DefNotFoundException> {
            incrementingMapKeysForQualifier(WatermarkRecord, WatermarkRecord.scalar.ref().toStorageByteArray() + byteArrayOf(0))
        }
    }

    @Test
    fun customModelStorageParsersRetainTheirReferenceSemantics() {
        val item = WatermarkRecord.ref { entries at 9u }
        val custom = object : IsRootDataModel by WatermarkRecord {
            override fun getPropertyReferenceByStorageBytes(
                length: Int,
                reader: () -> Byte,
                context: IsPropertyContext?,
            ): IsPropertyReference<*, IsPropertyDefinition<*>, *> = item
        }
        assertEquals<Any>(9u, incrementingMapKeysForQualifier(custom, byteArrayOf(9)).single().key)
    }
}
