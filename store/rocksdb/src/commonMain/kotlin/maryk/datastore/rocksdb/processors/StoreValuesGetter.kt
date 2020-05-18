package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB
import maryk.rocksdb.rocksDBNotFound

/** Reads requested values from the RocksDB store. */
internal class StoreValuesGetter(
    val key: ByteArray,
    val db: RocksDB,
    val columnFamilies: TableColumnFamilies,
    val readOptions: ReadOptions
) : IsValuesGetter {
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val reference = byteArrayOf(*key, *propertyReference.toStorageByteArray())

        val count = db.get(columnFamilies.table, readOptions, reference, recyclableByteArray)

        return when {
            count == rocksDBNotFound -> null
            count < recyclableByteArray.size -> recyclableByteArray
            else -> db.get(columnFamilies.table, readOptions, reference)
        }?.convertToValue(propertyReference, ULong.SIZE_BYTES, count - ULong.SIZE_BYTES)
    }
}
