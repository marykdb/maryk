package maryk.datastore.shared

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.MapValueReference

data class IncrementingMapMaximum(
    val reference: IncMapReference<out Comparable<Any>, out Any, *>,
    val key: Comparable<Any>,
)

fun incrementingMapWatermarkPrefix(modelId: UInt, keyBytes: ByteArray): ByteArray =
    "maryk.incmap/$modelId/".encodeToByteArray() + keyBytes.size.toVarBytes() + keyBytes

/** Extract allocation identities from qualifiers without decoding or decrypting values. */
fun incrementingMapKeysForQualifier(model: IsRootDataModel, qualifier: ByteArray): List<IncrementingMapMaximum> = buildList {
    if (qualifier.isEmpty() || (qualifier.size == 1 && (qualifier[0] == 0.toByte() || qualifier[0] == 8.toByte()))) return@buildList
    var readIndex = 0
    var reference: AnyPropertyReference? = model.getPropertyReferenceByStorageBytes(qualifier.size, { qualifier[readIndex++] })
    while (reference != null) {
        val parent = (reference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference
        if (reference is MapValueReference<*, *, *> && parent is IncMapReference<*, *, *>) {
            @Suppress("UNCHECKED_CAST")
            add(IncrementingMapMaximum(parent as IncMapReference<out Comparable<Any>, out Any, *>, reference.key as Comparable<Any>))
        }
        reference = parent
    }
}

fun encodeIncrementingMapMaximum(maximum: IncrementingMapMaximum): ByteArray {
    @Suppress("UNCHECKED_CAST")
    val definition = maximum.reference.propertyDefinition.definition.keyDefinition as IsStorageBytesEncodable<Comparable<Any>>
    return definition.toStorageBytes(maximum.key)
}

fun decodeIncrementingMapMaximum(reference: IncMapReference<out Comparable<Any>, out Any, *>, bytes: ByteArray): Comparable<Any> =
    reference.propertyDefinition.definition.keyDefinition.fromStorageBytes(bytes)

/** Pick the lifetime maximum, independently of whether its current table row still exists. */
fun incrementingMapAllocationPrefix(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
    currentQualifier: ByteArray?,
    maximum: Comparable<Any>?,
): ByteArray {
    val prefix = reference.toStorageByteArray()
    val keySize = reference.propertyDefinition.definition.keyDefinition.byteSize
    val currentPrefix = currentQualifier?.let { incrementingMapKeyPrefix(it, prefix, keySize) }
    val currentKey = currentPrefix?.let { decodeIncrementingMapMaximum(reference, it.copyOfRange(it.size - keySize, it.size)) }
    if (maximum != null && (currentKey == null || maximum > currentKey)) {
        return prefix + keySize.toVarBytes() + encodeIncrementingMapMaximum(IncrementingMapMaximum(reference, maximum))
    }
    return currentPrefix ?: (prefix + keySize.toVarBytes() + ByteArray(keySize) { 0xFF.toByte() })
}
