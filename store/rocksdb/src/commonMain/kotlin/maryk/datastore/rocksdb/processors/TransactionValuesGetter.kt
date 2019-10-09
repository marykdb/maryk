package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.rocksdb.ReadOptions

/** Reads requested values from the RocksDB [transaction]. */
internal class TransactionValuesGetter(
    val key: Key<*>,
    val transaction: Transaction,
    val columnFamilies: TableColumnFamilies,
    val readOptions: ReadOptions,
    val toVersion: ULong? = null
) : IsValuesGetter {
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val reference = byteArrayOf(*key.bytes, *propertyReference.toStorageByteArray())
        return transaction.getValue(columnFamilies, readOptions, toVersion, reference) { b, o, l ->
            b.convertToValue(propertyReference, o, l)
        }
    }
}
