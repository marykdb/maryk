package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.toULong
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB
import kotlin.math.max

/** Reads requested values from the RocksDB [db] for [key] in [columnFamilies] */
internal class StoreValuesGetter(
    var key: ByteArray?,
    val db: RocksDB,
    val columnFamilies: TableColumnFamilies,
    val readOptions: ReadOptions,
    private val captureVersion: Boolean = false
) : IsValuesGetter {
    private val cache = mutableMapOf<IsPropertyReference<*, *, *>, ByteArray?>()

    internal var lastVersion: ULong? = null

    /** Set the StoreValuesGetter to get the values for [key] */
    fun moveToKey(key: ByteArray) {
        this.key = key
        lastVersion = null
        cache.clear()
    }

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        key?.let { currentKey ->
            @Suppress("UNCHECKED_CAST")
            val valueAsBytes = cache.getOrPut(propertyReference) {
                val reference = byteArrayOf(*currentKey, *propertyReference.toStorageByteArray())

                db.get(columnFamilies.table, readOptions, reference)
            }

            return valueAsBytes?.convertToValue(propertyReference, ULong.SIZE_BYTES, valueAsBytes.size - ULong.SIZE_BYTES)?.also {
                if (captureVersion) {
                    val version = valueAsBytes.toULong()
                    this.lastVersion = this.lastVersion?.let {
                        max(it, version)
                    } ?: version
                }
            }
        } ?: throw Exception("No key passed to StoreValuesGetter")
    }
}
