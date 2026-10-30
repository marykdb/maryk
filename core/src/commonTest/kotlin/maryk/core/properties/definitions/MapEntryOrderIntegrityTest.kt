package maryk.core.properties.definitions

import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.core.values.Values
import kotlin.test.Test
import kotlin.test.assertEquals

private object MapEntryEmbedded : DataModel<MapEntryEmbedded>() {
    val previous by string(1u, required = false)
    val replacement by string(2u, required = false)
}

private object MapEntryOrderModel : RootDataModel<MapEntryOrderModel>() {
    val lists by map(1u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()), required = false)
    val nested by map(2u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()), required = false)
    val sets by map(3u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()), required = false)
    val embedded by map(4u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { MapEntryEmbedded }), required = false)
}

class MapEntryOrderIntegrityTest {
    @Test
    fun laterMapEntriesReplaceListValuesRegardlessOfFieldOrder() {
        val previous = MapEntryOrderModel.create(setDefaults = false) { lists with mapOf("key" to listOf("old1", "old2"), "other" to listOf("retained")) }
        val replacement = MapEntryOrderModel.create(setDefaults = false) { lists with mapOf("key" to listOf("new1", "new2")) }
        for (read in replace(previous, replacement)) {
            assertEquals(mapOf("key" to listOf("new1", "new2"), "other" to listOf("retained")), read.get { lists })
        }
    }

    @Test
    fun laterMapEntriesReplaceNestedMapsAndSetsRegardlessOfFieldOrder() {
        val oldMap = MapEntryOrderModel.create(setDefaults = false) { nested with mapOf("key" to mapOf("old1" to "one", "old2" to "two")) }
        val newMap = MapEntryOrderModel.create(setDefaults = false) { nested with mapOf("key" to mapOf("new1" to "three", "new2" to "four")) }
        for (read in replace(oldMap, newMap)) assertEquals(newMap.get { nested }, read.get { nested })

        val oldSet = MapEntryOrderModel.create(setDefaults = false) { sets with mapOf("key" to setOf("old1", "old2")) }
        val newSet = MapEntryOrderModel.create(setDefaults = false) { sets with mapOf("key" to setOf("new1", "new2")) }
        for (read in replace(oldSet, newSet)) assertEquals(newSet.get { sets }, read.get { sets })
    }

    @Test
    fun repeatedFieldsInsideOneEntryStillBuildCompleteCollections() {
        val values = MapEntryOrderModel.create(setDefaults = false) {
            lists with mapOf("key" to listOf("one", "two", "three"))
            nested with mapOf("key" to mapOf("one" to "first", "two" to "second"))
            sets with mapOf("key" to setOf("one", "two"))
        }
        assertEquals(values, decode(encode(values)))
        assertEquals(values, decode(valueFieldsBeforeKeys(encode(values))))
    }

    @Test
    fun emptyAndEmbeddedReplacementsDoNotRetainEarlierFields() {
        val oldList = MapEntryOrderModel.create(setDefaults = false) { lists with mapOf("key" to listOf("old")) }
        val emptyList = MapEntryOrderModel.create(setDefaults = false) { lists with mapOf("key" to emptyList()) }
        for (read in replace(oldList, emptyList)) assertEquals(emptyList, read)

        val previousValue = MapEntryEmbedded.create(setDefaults = false) { previous with "old" }
        val replacementValue = MapEntryEmbedded.create(setDefaults = false) { replacement with "new" }
        val previous = MapEntryOrderModel.create(setDefaults = false) { embedded with mapOf("key" to previousValue) }
        val replacement = MapEntryOrderModel.create(setDefaults = false) { embedded with mapOf("key" to replacementValue) }
        for (read in replace(previous, replacement)) assertEquals(replacement, read)
    }

    private fun replace(previous: Values<MapEntryOrderModel>, replacement: Values<MapEntryOrderModel>): List<Values<MapEntryOrderModel>> {
        val oldBytes = encode(previous)
        val newBytes = encode(replacement)
        return listOf(decode(oldBytes + newBytes), decode(oldBytes + valueFieldsBeforeKeys(newBytes)))
    }

    private fun encode(values: Values<MapEntryOrderModel>): ByteArray {
        val cache = WriteCache()
        val bytes = ByteArray(MapEntryOrderModel.Serializer.calculateProtoBufLength(values, cache))
        var offset = 0
        MapEntryOrderModel.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it })
        return bytes
    }

    private fun decode(bytes: ByteArray): Values<MapEntryOrderModel> {
        var offset = 0
        return MapEntryOrderModel.Serializer.readProtoBuf(bytes.size, { bytes[offset++] })
    }

    /** Preserve all fields and value-field order, moving only the entry key after them. */
    private fun valueFieldsBeforeKeys(bytes: ByteArray): ByteArray {
        val output = mutableListOf<Byte>()
        var offset = 0
        while (offset < bytes.size) {
            val start = offset
            val field = ProtoBuf.readKey { bytes[offset++] }
            assertEquals(LENGTH_DELIMITED, field.wireType)
            val length = ProtoBuf.getLength(field.wireType) { bytes[offset++] }
            val end = offset + length
            output.addAll(bytes.copyOfRange(start, offset).toList())
            val keys = mutableListOf<Byte>()
            while (offset < end) {
                val entryStart = offset
                val entryField = ProtoBuf.readKey { bytes[offset++] }
                ProtoBuf.skipField(entryField) { bytes[offset++] }
                (if (entryField.tag == 1u) keys else output).addAll(bytes.copyOfRange(entryStart, offset).toList())
            }
            output.addAll(keys)
            assertEquals(end, offset)
        }
        return output.toByteArray()
    }
}
