package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Range
import maryk.foundationdb.ReadTransaction
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsMapReference
import maryk.datastore.shared.readValue

/** Read direct map entries for [mapReference] at [keyBytes] from latest table rows. */
internal fun ReadTransaction.readMapByReference(
    tablePrefix: ByteArray,
    keyBytes: ByteArray,
    mapReference: IsMapReference<Any, Any, IsPropertyContext, *>,
    decryptValue: ((ByteArray) -> ByteArray)? = null
): Map<Any, Any>? {
    val mapDefinition = mapReference.propertyDefinition.definition
    val mapValueDefinition = mapDefinition.valueDefinition
    val mapPrefix = packKey(tablePrefix, keyBytes, mapReference.toStorageByteArray())
    val map = linkedMapOf<Any, Any>()

    val iterator = this.getRange(Range.startsWith(mapPrefix)).iterator()
    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        val qualifier = kv.key

        val mapKey = try {
            var readIndex = mapPrefix.size
            val mapKeyLength = initIntByVar { qualifier[readIndex++] }
            val keyValue = mapDefinition.keyDefinition.readStorageBytes(mapKeyLength) { qualifier[readIndex++] }
            if (readIndex != qualifier.size) continue
            keyValue
        } catch (_: Throwable) {
            continue
        }

        val stored = kv.value
        val plain = decryptValue?.invoke(stored.copyOfRange(VERSION_BYTE_SIZE, stored.size))
            ?: stored.copyOfRange(VERSION_BYTE_SIZE, stored.size)
        var valueReadIndex = 0
        val value = readValue(mapValueDefinition, { plain[valueReadIndex++] }) { plain.size - valueReadIndex } ?: continue
        map[mapKey] = value
    }

    return map.takeIf { it.isNotEmpty() }
}
