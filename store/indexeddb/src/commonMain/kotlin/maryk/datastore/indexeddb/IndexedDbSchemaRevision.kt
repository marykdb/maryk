package maryk.datastore.indexeddb

import kotlin.uuid.Uuid

/** Native adapters atomically stamp every change to guarded schema metadata. */
internal interface SchemaRevisionAwareIndexedDbStore

/** Return the schema revision followed by record heads from one native read transaction. */
internal interface IndexedDbSchemaReadStore : SchemaRevisionAwareIndexedDbStore {
    suspend fun getRecordsWithSchemaRevision(storeName: String, keys: List<ByteArray>): List<ByteArray?>

    /** Transform the revision first, then each head in the same native read view. */
    suspend fun <T> mapRecordsWithSchemaRevision(storeName: String, keys: List<ByteArray>, transform: (Int, ByteArray?) -> T): List<T> =
        getRecordsWithSchemaRevision(storeName, keys).mapIndexed(transform)
}

internal val SchemaRevisionMetadataKey = "maryk.schema/revision".encodeToByteArray()

internal fun IndexedDbWriteOperation.changesSchemaMetadata(): Boolean =
    storeName == "meta" && isSchemaMetadataKey(key)

internal suspend fun IndexedDbByteStore.withSchemaRevision(
    operations: List<IndexedDbWriteOperation>,
): List<IndexedDbWriteOperation> {
    // The caller holds its fenced writer lease. Compare before adding a stamp so
    // opening another context with identical definitions does not invalidate it.
    for (operation in operations) {
        if (!operation.changesSchemaMetadata()) continue
        val previous = get("meta", operation.key)
        val next = (operation as? IndexedDbWriteOperation.Put)?.value
        if (!previous.contentEquals(next)) {
            return operations + IndexedDbWriteOperation.Put(
                "meta", SchemaRevisionMetadataKey, Uuid.random().toByteArray(),
            )
        }
    }
    return operations
}
