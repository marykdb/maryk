package maryk.datastore.foundationdb

import maryk.core.properties.types.Bytes
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Attempt-local reads for atomic adds with distinct targets; recreated on every FDB retry. */
internal class FoundationDBBulkAddContext(val owner: FoundationDBDataStore) :
    AbstractCoroutineContextElement(Key) {
    val schemaCheckedModels = mutableSetOf<UInt>()
    val initialRecords = mutableMapOf<Pair<UInt, Bytes>, FoundationDBInitialAddState>()

    companion object Key : CoroutineContext.Key<FoundationDBBulkAddContext>
}

internal data class FoundationDBInitialAddState(
    val existing: ByteArray?,
    val tombstoneVersion: ULong?,
)
