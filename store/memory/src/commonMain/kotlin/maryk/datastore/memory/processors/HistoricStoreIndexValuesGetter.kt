package maryk.datastore.memory.processors

import maryk.core.exceptions.StorageException
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.lib.extensions.compare.compareTo

/** Walks all historical index values from the Memory store for a record. */
internal object HistoricStoreIndexValuesWalker {
    fun walkIndexHistory(
        record: DataRecord<*, *>,
        indexable: IsIndexable,
        handleIndexReference: (ByteArray, ULong) -> Unit
    ) {
        val getter = HistoricStoreIndexValuesGetter(record)

        var lastVersion: ULong?
        do {
            try {
                indexable.toStorageByteArrayForIndex(getter, record.key.bytes)?.let { historicIndexReference ->
                    handleIndexReference(
                        historicIndexReference,
                        getter.latestOverallVersion ?: throw StorageException("Latest overall version not set")
                    )
                }
            } catch (e: Throwable) {
                // skip failing index reference generation
            }

            lastVersion = getter.versionToSkip
            getter.versionToSkip = getter.latestOverallVersion
        } while (getter.versionToSkip != lastVersion)
    }
}

private class HistoricStoreIndexValuesGetter(
    val dataRecord: DataRecord<*, *>
) : IsValuesGetter {
    val iterableReferenceMap = mutableMapOf<IsPropertyReference<*, *, *>, IterableReference<*>>()
    var latestOverallVersion: ULong? = null
    var versionToSkip: ULong? = null

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        @Suppress("UNCHECKED_CAST")
        val iterableReference = iterableReferenceMap.getOrPut(
            propertyReference
        ) {
            val referenceAsBytes = propertyReference.toStorageByteArray()

            val valueIndex = dataRecord.values.binarySearch {
                it.reference.compareTo(referenceAsBytes)
            }

            if (valueIndex < 0) {
                return null
            }

            IterableReference<T>(
                dataRecord.values[valueIndex]
            )
        } as IterableReference<T>

        if (iterableReference.latestRecord == null || latestOverallVersion == iterableReference.latestRecord!!.version.timestamp) {
            @Suppress("UNCHECKED_CAST")
            iterableReference.latestRecord = when (val node = iterableReference.dataRecordNode) {
                is DataRecordValue<*> -> {
                    if (versionToSkip?.let { node.version.timestamp >= it } == true) {
                        null
                    } else {
                        node as DataRecordValue<T>
                    }
                }
                is DataRecordHistoricValues<*> -> {
                    node.history.getOrNull(iterableReference.nextHistoryIndex++) as DataRecordValue<T>
                }
                else -> throw StorageException("Unknown storage type: $node")
            }

            iterableReference.latestRecord?.let { latestRecord ->
                latestOverallVersion = latestOverallVersion?.let {
                    maxOf(latestRecord.version.timestamp, it)
                } ?: latestRecord.version.timestamp
            }
        }

        return iterableReference.latestRecord?.value
    }
}

internal class IterableReference<T: Any>(
    val dataRecordNode: DataRecordNode,
    var nextHistoryIndex: Int = 0,
    var latestRecord: DataRecordValue<T>? = null
)
