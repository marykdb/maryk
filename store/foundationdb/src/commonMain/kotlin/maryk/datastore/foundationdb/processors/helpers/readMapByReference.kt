package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Range
import maryk.foundationdb.ReadTransaction
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsMapReference
import maryk.datastore.shared.isSkippableDataError
import maryk.datastore.shared.readValue
import maryk.datastore.shared.rethrowIfFatal

/** Read direct map entries for [mapReference] at [keyBytes] from latest table rows. */
internal fun ReadTransaction.readMapByReference(
    tablePrefix: ByteArray,
    keyBytes: ByteArray,
    mapReference: IsMapReference<Any, Any, IsPropertyContext, *>,
    decryptValue: DecryptValue? = null
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
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            if (!error.isSkippableDataError()) {
                throw error
            }
            continue
        }

        try {
            val stored = kv.value
            val value = stored.withCurrentPayload(decryptValue) { payload, offset, length ->
                var valueReadIndex = offset
                readValue(mapValueDefinition, { payload[valueReadIndex++] }) { offset + length - valueReadIndex }
            } ?: continue
            map[mapKey] = value
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            if (!error.isSkippableDataError()) {
                throw error
            }
            continue
        }
    }

    return map.takeIf { it.isNotEmpty() }
}
