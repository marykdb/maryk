package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB

/** Reads requested values from the RocksDB store. */
internal class StoreValuesGetter(
    val key: Key<*>,
    val db: RocksDB,
    val columnFamilies: TableColumnFamilies,
    val readOptions: ReadOptions
) : IsValuesGetter {
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val reference = byteArrayOf(*key.bytes, *propertyReference.toStorageByteArray())

        return db.get(columnFamilies.table, readOptions, reference)?.let { b ->
            b.convertToValue(propertyReference, ULong.SIZE_BYTES, b.size - ULong.SIZE_BYTES)
        }
    }
}
