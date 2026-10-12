package maryk.datastore.foundationdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.lib.extensions.compare.compareTo

/** Native reads are started together, but decoded in request order in the owning transaction. */
internal class PreparedRecordRead(
    private val points: List<Pair<ByteArray, FdbFuture<ByteArray?>>>,
    private val ranges: List<FdbFuture<List<KeyValue>>>,
) {
    fun awaitRows(): List<KeyValue> = try {
        if (points.isEmpty() && ranges.size == 1) {
            // FoundationDB already returns each range in storage order.
            ranges.single().awaitResult()
        } else {
            val rows = ArrayList<KeyValue>()
            points.forEach { (key, future) -> future.awaitResult()?.let { rows += KeyValue(key, it) } }
            ranges.forEach { rows.addAll(it.awaitResult()) }
            rows.sortWith { first, second -> first.key.compareTo(second.key) }
            rows
        }
    } finally {
        cancel()
    }

    fun cancel() {
        points.forEach { it.second.cancel() }
        ranges.forEach { it.cancel() }
    }
}

internal fun <DM : IsRootDataModel> DM.prepareRecordRead(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    select: RootPropRefGraph<DM>?,
    toVersion: ULong?,
): PreparedRecordRead {
    val prefix = if (toVersion == null) tableDirs.tablePrefix else {
        (tableDirs as? HistoricTableDirectories)?.historicTablePrefix
            ?: throw RequestException("No historic table present so cannot use `toVersion` on get")
    }
    val recordPrefix = packKey(prefix, key)
    // A scalar qualifier has no child rows. Collections and nested graph selections
    // retain the full range decoder, including size/type/deletion markers.
    val selected = select?.properties ?: if (toVersion != null) this.toList() else null
    val scalarProperties = selected?.takeIf { properties ->
        properties.size in 1..16 && properties.all {
            it is IsDefinitionWrapper<*, *, *, *> && it.definition is IsSimpleValueDefinition<*, *>
        }
    }
    if (scalarProperties == null) {
        return PreparedRecordRead(emptyList(), listOf(tr.getRange(Range.startsWith(recordPrefix)).asList()))
    }

    val qualifiers = scalarProperties.map {
        (it as IsDefinitionWrapper<*, *, *, *>).ref().toStorageByteArray()
    } + listOf(byteArrayOf(SOFT_DELETE_INDICATOR))
    if (toVersion == null) {
        val lastQualifier = qualifiers.maxWith { first, second -> first.compareTo(second) }
        val selectedIndexes = scalarProperties.map { (it as IsDefinitionWrapper<*, *, *, *>).index }.toSet()
        val contiguousSelection = this.all { property ->
            property.ref().toStorageByteArray().compareTo(lastQualifier) > 0 || property.index in selectedIndexes
        }
        if (contiguousSelection) {
            // A leading selection plus the record/delete markers fits in one range.
            // Gapped selections use parallel point reads to avoid intervening payloads.
            return PreparedRecordRead(emptyList(), listOf(tr.getRange(Range(
                recordPrefix, packKey(recordPrefix, lastQualifier, byteArrayOf(0)),
            )).asList()))
        }
    }
    val marker = recordPrefix to tr.get(recordPrefix)
    return if (toVersion == null) {
        PreparedRecordRead(listOf(marker) + qualifiers.map { qualifier ->
            val storageKey = packKey(recordPrefix, qualifier)
            storageKey to tr.get(storageKey)
        }, emptyList())
    } else {
        val reversedVersion = toVersion.toReversedVersionBytes()
        PreparedRecordRead(listOf(marker), qualifiers.map { qualifier ->
            val qualifierPrefix = packKey(recordPrefix, encodeZeroFreeUsing01(qualifier))
            // Separator 0 precedes all child qualifiers. Keep the visible delete marker
            // rather than falling through to an older value.
            tr.getRange(
                packKey(qualifierPrefix, byteArrayOf(0), reversedVersion),
                packKey(qualifierPrefix, byteArrayOf(1)),
                1,
            ).asList()
        })
    }
}
