package maryk.datastore.rocksdb

import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.test.legacyFloatBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object DescriptorUpgradeLeaf : DataModel<DescriptorUpgradeLeaf>() {
    val n by number(1u, type = SInt32)
}

private object DescriptorUpgradeRecord : RootDataModel<DescriptorUpgradeRecord>(indexes = {
    listOf(DescriptorUpgradeRecord.indexedNumber as IsIndexable)
}) {
    val items by map(1u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = EmbeddedValuesDefinition(dataModel = { DescriptorUpgradeLeaf }))
    val indexedNumber get() = ref { items at -1.0 }.getEmbedded("n", null)
}

private fun ByteArray.replacePrefix(current: ByteArray, released: ByteArray): ByteArray =
    if (size >= current.size && current.indices.all { this[it] == current[it] }) released + copyOfRange(current.size, size) else this

class IndexDescriptorStorageUpgradeTest {
    @Test
    fun upgradesIntegerLeafIndexDescriptorUnderFloatingMapKey() = runTest {
        val folder = createTestDBFolder("floating-ancestor-index-descriptor")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to DescriptorUpgradeRecord), keepAllVersions = true)
        var store = open()
        try {
            val values = DescriptorUpgradeRecord.create { items with mapOf(-1.0 to DescriptorUpgradeLeaf.create { n with 123 }) }
            val added = assertIs<AddSuccess<DescriptorUpgradeRecord>>(store.execute(DescriptorUpgradeRecord.add(values)).statuses.single())
            val families = store.getColumnFamilies(1u) as HistoricTableColumnFamilies
            val currentMap = DescriptorUpgradeRecord.ref { items at -1.0 }.toStorageByteArray()
            val releasedMap = currentMap.copyOf().apply { legacyFloatBytes(-1.0).copyInto(this, size - 8) }
            for ((family, historic) in listOf(families.table to false, families.historic.table to true)) {
                val rows = mutableListOf<Pair<ByteArray, ByteArray>>()
                store.db.newIterator(family).use { iterator ->
                    iterator.seekToFirst()
                    while (iterator.isValid()) { rows += iterator.key() to iterator.value(); iterator.next() }
                }
                for ((key, value) in rows) {
                    val end = key.size - if (historic) 8 else 0
                    if (end <= added.key.bytes.size) continue
                    val qualifier = key.copyOfRange(added.key.bytes.size, end)
                    val target = key.copyOfRange(0, added.key.bytes.size) + qualifier.replacePrefix(currentMap, releasedMap) + key.copyOfRange(end, key.size)
                    store.db.delete(family, key); store.db.put(family, target, value)
                }
            }
            val currentDescriptor = (DescriptorUpgradeRecord.indexedNumber as IsIndexable).referenceStorageByteArray.bytes
            val releasedDescriptor = "5a0c08c01000000000000009".hexToByteArray()
            for (family in listOf(families.index, families.historic.index)) {
                val rows = mutableListOf<Pair<ByteArray, ByteArray>>()
                store.db.newIterator(family).use { iterator ->
                    iterator.seekToFirst()
                    while (iterator.isValid()) { rows += iterator.key() to iterator.value(); iterator.next() }
                }
                for ((key, value) in rows) {
                    val target = key.replacePrefix(byteArrayOf(currentDescriptor.size.toByte()) + currentDescriptor,
                        byteArrayOf(releasedDescriptor.size.toByte()) + releasedDescriptor)
                    store.db.delete(family, key); store.db.put(family, target, value)
                }
            }
            store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
            store.close(); store = open()
            for (version in listOf(null, added.version)) {
                val result = store.execute(DescriptorUpgradeRecord.scan(order = DescriptorUpgradeRecord.indexedNumber.ascending(), toVersion = version))
                assertIs<FetchByIndexScan>(result.dataFetchType)
                assertEquals(listOf(added.key), result.values.map { it.key })
                assertEquals(values, result.values.single().values)
            }
        } finally { store.close(); deleteFolder(folder) }
    }
}
