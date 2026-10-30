package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.references.AnyPropertyReference
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.shared.hasActiveStoredType
import maryk.datastore.shared.storedTypeIndex
import maryk.rocksdb.ReadOptions

internal fun DBAccessor.hasActiveStoredType(
    families: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    reference: AnyPropertyReference,
    toVersion: ULong?,
    historicalReader: HistoricalTableReader? = null,
): Boolean = hasActiveStoredType(reference) { parent ->
    getValue(families, readOptions, toVersion, key + parent.toStorageByteArray(), historicalReader) { bytes, offset, length ->
        storedTypeIndex(bytes, offset, length)
    }
}
