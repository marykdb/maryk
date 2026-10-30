package maryk.datastore.foundationdb

import kotlin.random.Random
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.foundationdb.Range
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
        val directory = listOf("maryk", "floating-ancestor-index-descriptor-${Random.nextInt()}")
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to DescriptorUpgradeRecord), keepAllVersions = true)
        var store = open()
        try {
            val values = DescriptorUpgradeRecord.create { items with mapOf(-1.0 to DescriptorUpgradeLeaf.create { n with 123 }) }
            val added = assertIs<AddSuccess<DescriptorUpgradeRecord>>(store.execute(DescriptorUpgradeRecord.add(values)).statuses.single())
            val dirs = store.getTableDirs(1u) as HistoricTableDirectories
            val currentMap = DescriptorUpgradeRecord.ref { items at -1.0 }.toStorageByteArray()
            val releasedMap = currentMap.copyOf().apply { legacyFloatBytes(-1.0).copyInto(this, size - 8) }
            for ((prefix, historic) in listOf(dirs.tablePrefix to false, dirs.historicTablePrefix to true)) {
                store.tc.run { transaction ->
                    val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
                    val rows = buildList { while (iterator.hasNext()) add(iterator.nextBlocking()) }
                    for (row in rows) {
                        val end = row.key.size - if (historic) 9 else 0
                        val start = prefix.size + added.key.bytes.size
                        if (end <= start) continue
                        val qualifier = if (historic) decodeZeroFreeUsing01(row.key, start, end - start) else row.key.copyOfRange(start, end)
                        val released = qualifier.replacePrefix(currentMap, releasedMap)
                        val target = row.key.copyOfRange(0, start) + (if (historic) encodeZeroFreeUsing01(released) else released) + row.key.copyOfRange(end, row.key.size)
                        transaction.clear(row.key); transaction.set(target, row.value)
                    }
                }
            }
            val currentDescriptor = (DescriptorUpgradeRecord.indexedNumber as IsIndexable).referenceStorageByteArray.bytes
            val releasedDescriptor = "5a0c08c01000000000000009".hexToByteArray()
            for ((prefix, historic) in listOf(dirs.indexPrefix to false, dirs.historicIndexPrefix to true)) {
                store.tc.run { transaction ->
                    val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
                    val rows = buildList { while (iterator.hasNext()) add(iterator.nextBlocking()) }
                    for (row in rows) {
                        val end = row.key.size - if (historic) 9 else 0
                        val content = if (historic) decodeZeroFreeUsing01(row.key, prefix.size, end - prefix.size) else row.key.copyOfRange(prefix.size, end)
                        val released = content.replacePrefix(currentDescriptor, releasedDescriptor)
                        val target = prefix + (if (historic) encodeZeroFreeUsing01(released) else released) + row.key.copyOfRange(end, row.key.size)
                        transaction.clear(row.key); transaction.set(target, row.value)
                    }
                }
            }
            store.tc.run { it.clear(store.storageFormatPrefix + StorageFormatMetadataKey) }
            store.close(); store = open()
            for (version in listOf(null, added.version)) {
                val result = store.execute(DescriptorUpgradeRecord.scan(order = DescriptorUpgradeRecord.indexedNumber.ascending(), toVersion = version))
                assertIs<FetchByIndexScan>(result.dataFetchType)
                assertEquals(listOf(added.key), result.values.map { it.key })
                assertEquals(values, result.values.single().values)
            }
        } finally { store.close() }
    }
}
