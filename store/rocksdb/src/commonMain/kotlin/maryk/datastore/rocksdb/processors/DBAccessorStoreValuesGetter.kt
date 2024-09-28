package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import org.rocksdb.ReadOptions
import kotlin.math.max

/**
 * A values getter which searches on the passed DBAccessor or Transaction
 */
internal class DBAccessorStoreValuesGetter(
    val columnFamilies: TableColumnFamilies,
    val readOptions: ReadOptions,
    private val captureVersion: Boolean = false
) : IsValuesGetter {
    private val cache = mutableMapOf<IsPropertyReference<*, *, *>, Any?>()

    private var toVersion: ULong? = ULong.MAX_VALUE
    internal var lastVersion: ULong? = null

    lateinit var dbAccessor: DBAccessor
    lateinit var key: ByteArray

    /** Set the getter to get the values for [key] from [dbAccessor] until [toVersion] */
    fun moveToKey(key: ByteArray, dbAccessor: DBAccessor, toVersion: ULong? = null) {
        this.key = key
        this.dbAccessor = dbAccessor
        this.toVersion = toVersion
        this.lastVersion = null
        cache.clear()
    }

    /** Get latest value for property of [propertyReference] */
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(propertyReference) {
            dbAccessor.getValue(columnFamilies, readOptions, this.toVersion, key + propertyReference.toStorageByteArray()) { valueAsBytes, offset, length ->
                (valueAsBytes.convertToValue(propertyReference, offset, length) as T?)?.also {
                    if (captureVersion) {
                        val version = valueAsBytes.readVersionBytes()
                        this.lastVersion = this.lastVersion?.let {
                            max(it, version)
                        } ?: version
                    }
                }
            }
        } as T?
    }
}
