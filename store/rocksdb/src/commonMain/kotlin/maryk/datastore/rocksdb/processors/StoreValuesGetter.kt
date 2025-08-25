package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.helpers.convertToValue
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

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? =
        key?.let { currentKey ->
            val valueAsBytes = cache.getOrPut(propertyReference) {
                val reference = currentKey + propertyReference.toStorageByteArray()

                db.get(columnFamilies.table, readOptions, reference)
            }

            valueAsBytes?.convertToValue(propertyReference,
                VERSION_BYTE_SIZE, valueAsBytes.size - VERSION_BYTE_SIZE
            )?.also {
                if (captureVersion) {
                    val version = valueAsBytes.readVersionBytes()
                    this.lastVersion = this.lastVersion?.let {
                        max(it, version)
                    } ?: version
                }
            }
        } ?: throw Exception("No key passed to StoreValuesGetter")
}
