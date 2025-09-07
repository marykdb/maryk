package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.Range
import com.apple.foundationdb.TransactionContext
import maryk.core.properties.definitions.index.IsIndexable
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey

/**
 * Deletes all index values at [tableDirectories] for [indexable] for both
 * non-historic and historic index trees (when present).
 */
internal fun deleteCompleteIndexContents(
    tc: TransactionContext,
    tableDirectories: IsTableDirectories,
    indexable: IsIndexable
) {
    val indexRef = indexable.referenceStorageByteArray.bytes
    tc.run { tr ->
        // Clear non-historic index entries for this index reference
        val nonHistoricPrefix = packKey(tableDirectories.indexPrefix, indexRef)
        tr.clear(Range.startsWith(nonHistoricPrefix))

        // Clear historic index entries if historical tables are present
        if (tableDirectories is HistoricTableDirectories) {
            val histBase = tableDirectories.historicIndexPrefix
            val encodedRef = encodeZeroFreeUsing01(indexRef)
            val histPrefix = packKey(histBase, encodedRef)
            tr.clear(Range.startsWith(histPrefix))
        }
    }
}
