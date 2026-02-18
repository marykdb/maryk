package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.readValue
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.extensions.compare.matchesRangePart
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
    private val cache = mutableMapOf<IsPropertyReference<*, *, *>, Any?>()

    internal var lastVersion: ULong? = null

    /** Set the StoreValuesGetter to get the values for [key] */
    fun moveToKey(key: ByteArray) {
        this.key = key
        lastVersion = null
        cache.clear()
    }

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val currentKey = key ?: return null

        if (cache.containsKey(propertyReference)) {
            @Suppress("UNCHECKED_CAST")
            return cache[propertyReference] as T?
        }

        val value = if (propertyReference is IsMapReference<*, *, *, *>) {
            @Suppress("UNCHECKED_CAST")
            readMapValue(currentKey, propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>?) as T?
        } else if (propertyReference is SetReference<*, *>) {
            @Suppress("UNCHECKED_CAST")
            readSetValue(currentKey, propertyReference as SetReference<Any, IsPropertyContext>?) as T?
        } else {
            val reference = currentKey + propertyReference.toStorageByteArray()
            val valueAsBytes = db.get(columnFamilies.table, readOptions, reference)
            valueAsBytes?.convertToValue(
                propertyReference,
                VERSION_BYTE_SIZE, valueAsBytes.size - VERSION_BYTE_SIZE
            )?.also {
                if (captureVersion) {
                    val version = valueAsBytes.readVersionBytes()
                    this.lastVersion = this.lastVersion?.let {
                        max(it, version)
                    } ?: version
                }
            }
        }

        cache[propertyReference] = value
        return value
    }

    private fun readMapValue(
        key: ByteArray,
        propertyReference: IsMapReference<Any, Any, IsPropertyContext, *>?
    ): Map<Any, Any>? {
        if (propertyReference == null) return null
        val mapDefinition = propertyReference.propertyDefinition.definition
        val mapValueDefinition = mapDefinition.valueDefinition
        val mapPrefix = key + propertyReference.toStorageByteArray()
        val map = linkedMapOf<Any, Any>()

        db.newIterator(columnFamilies.table, readOptions).use { iterator ->
            iterator.seek(mapPrefix)
            while (iterator.isValid()) {
                val qualifier = iterator.key()
                if (!qualifier.matchesRangePart(0, mapPrefix)) break

                val mapKey = try {
                    var readIndex = mapPrefix.size
                    val mapKeyLength = initIntByVar { qualifier[readIndex++] }
                    val keyValue = mapDefinition.keyDefinition.readStorageBytes(mapKeyLength) { qualifier[readIndex++] }
                    if (readIndex != qualifier.size) {
                        iterator.next()
                        continue
                    }
                    keyValue
                } catch (_: Throwable) {
                    iterator.next()
                    continue
                }

                val storedValue = iterator.value()
                val valueBytes = storedValue.copyOfRange(VERSION_BYTE_SIZE, storedValue.size)
                var valueReadIndex = 0
                val value = readValue(mapValueDefinition, { valueBytes[valueReadIndex++] }) { valueBytes.size - valueReadIndex }
                    ?: run {
                        iterator.next()
                        continue
                    }
                map[mapKey] = value
                iterator.next()
            }
        }

        return map.takeIf { it.isNotEmpty() }
    }

    private fun readSetValue(
        key: ByteArray,
        propertyReference: SetReference<Any, IsPropertyContext>?
    ): Set<Any>? {
        if (propertyReference == null) return null
        val setDefinition = propertyReference.propertyDefinition.definition
        val setPrefix = key + propertyReference.toStorageByteArray()
        val set = linkedSetOf<Any>()

        db.newIterator(columnFamilies.table, readOptions).use { iterator ->
            iterator.seek(setPrefix)
            while (iterator.isValid()) {
                val qualifier = iterator.key()
                if (!qualifier.matchesRangePart(0, setPrefix)) break

                val setItem = try {
                    var readIndex = setPrefix.size
                    val setItemLength = initIntByVar { qualifier[readIndex++] }
                    @Suppress("UNCHECKED_CAST")
                    val valueDefinition = setDefinition.valueDefinition as maryk.core.properties.definitions.IsStorageBytesEncodable<Any>
                    val itemValue = valueDefinition.readStorageBytes(setItemLength) { qualifier[readIndex++] }
                    if (readIndex != qualifier.size) {
                        iterator.next()
                        continue
                    }
                    itemValue
                } catch (_: Throwable) {
                    iterator.next()
                    continue
                }

                set += setItem
                iterator.next()
            }
        }

        return set.takeIf { it.isNotEmpty() }
    }
}
