package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.TransactionContext
import maryk.core.properties.definitions.index.IsIndexable
import maryk.datastore.foundationdb.IsTableDirectories

/**
 * Deletes all index values at [tableDirectories] for [indexable]
 */
internal fun deleteCompleteIndexContents(tc: TransactionContext, tableDirectories: IsTableDirectories, indexable: IsIndexable) {
    TODO("Not yet implemented")
}
