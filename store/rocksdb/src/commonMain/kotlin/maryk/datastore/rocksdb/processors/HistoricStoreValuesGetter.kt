package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.rocksdb.ReadOptions

/**
 * A historical values getter which finds the first valid value from [toVersion]
 */
internal class HistoricStoreValuesGetter(
    val columnFamilies: TableColumnFamilies,
    val readOptions: ReadOptions
) : IsValuesGetter {
    private val cache = mutableMapOf<IsPropertyReference<*, *, *>, Any?>()

    private var toVersion: ULong? = ULong.MAX_VALUE

    lateinit var dbAccessor: DBAccessor
    lateinit var key: ByteArray

    /** Set the getter to get the values for [key] from [dbAccessor] until [toVersion] */
    fun moveToKey(key: ByteArray, dbAccessor: DBAccessor, toVersion: ULong?) {
        this.key = key
        this.dbAccessor = dbAccessor
        this.toVersion = toVersion
        cache.clear()
    }

    /** Get latest value for property of [propertyReference] */
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(propertyReference) {
            dbAccessor.getValue(columnFamilies, readOptions, this.toVersion, byteArrayOf(*key, *propertyReference.toStorageByteArray())) { valueAsBytes, offset, length ->
                valueAsBytes.convertToValue(propertyReference, offset, length)
            }
        } as T?
    }
}
