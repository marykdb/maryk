package maryk.datastore.foundationdb.processors.helpers

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.SetReference
import maryk.foundationdb.Range
import maryk.foundationdb.ReadTransaction

/** Read direct set entries for [setReference] at [keyBytes] from latest table rows. */
internal fun <T : Any> ReadTransaction.readSetByReference(
    tablePrefix: ByteArray,
    keyBytes: ByteArray,
    setReference: SetReference<T, IsPropertyContext>
): Set<T>? {
    val setDefinition = setReference.propertyDefinition.definition
    val setPrefix = packKey(tablePrefix, keyBytes, setReference.toStorageByteArray())
    val set = linkedSetOf<T>()

    val iterator = this.getRange(Range.startsWith(setPrefix)).iterator()
    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        val qualifier = kv.key

        val setItem = try {
            var readIndex = setPrefix.size
            val setItemLength = initIntByVar { qualifier[readIndex++] }
            @Suppress("UNCHECKED_CAST")
            val valueDefinition = setDefinition.valueDefinition as maryk.core.properties.definitions.IsStorageBytesEncodable<T>
            val itemValue = valueDefinition.readStorageBytes(setItemLength) { qualifier[readIndex++] }
            if (readIndex != qualifier.size) continue
            itemValue
        } catch (_: Throwable) {
            continue
        }

        set += setItem
    }

    return set.takeIf { it.isNotEmpty() }
}
