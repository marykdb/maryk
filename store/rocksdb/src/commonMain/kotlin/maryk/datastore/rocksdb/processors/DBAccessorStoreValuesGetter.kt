package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.readValue
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions
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
        if (cache.containsKey(propertyReference)) {
            @Suppress("UNCHECKED_CAST")
            return cache[propertyReference] as T?
        }

        val value = if (toVersion == null && propertyReference is IsMapReference<*, *, *, *>) {
            @Suppress("UNCHECKED_CAST")
            readMapValue(propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>?) as T?
        } else if (toVersion == null && propertyReference is SetReference<*, *>) {
            @Suppress("UNCHECKED_CAST")
            readSetValue(propertyReference as SetReference<Any, IsPropertyContext>?) as T?
        } else {
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
        }

        cache[propertyReference] = value
        return value
    }

    private fun readMapValue(
        propertyReference: IsMapReference<Any, Any, IsPropertyContext, *>?
    ): Map<Any, Any>? {
        if (propertyReference == null) return null
        val mapDefinition = propertyReference.propertyDefinition.definition
        val mapValueDefinition = mapDefinition.valueDefinition
        val mapPrefix = key + propertyReference.toStorageByteArray()
        val map = linkedMapOf<Any, Any>()

        dbAccessor.getIterator(readOptions, columnFamilies.table).use { iterator ->
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
                val valueBytes = dbAccessor.dataStore.decryptValueIfNeeded(storedValue.copyOfRange(VERSION_BYTE_SIZE, storedValue.size))
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
        propertyReference: SetReference<Any, IsPropertyContext>?
    ): Set<Any>? {
        if (propertyReference == null) return null
        val setDefinition = propertyReference.propertyDefinition.definition
        val setPrefix = key + propertyReference.toStorageByteArray()
        val set = linkedSetOf<Any>()

        dbAccessor.getIterator(readOptions, columnFamilies.table).use { iterator ->
            iterator.seek(setPrefix)
            while (iterator.isValid()) {
                val qualifier = iterator.key()
                if (!qualifier.matchesRangePart(0, setPrefix)) break

                val setItem = try {
                    var readIndex = setPrefix.size
                    val setItemLength = initIntByVar { qualifier[readIndex++] }
                    @Suppress("UNCHECKED_CAST")
                    val valueDefinition = setDefinition.valueDefinition as IsStorageBytesEncodable<Any>
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
