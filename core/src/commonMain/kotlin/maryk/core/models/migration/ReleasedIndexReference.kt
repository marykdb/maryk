package maryk.core.models.migration

import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.LegacyNormalize
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypeReference

/** The released descriptor can contain legacy bytes in a fixed map/set ancestor key. */
fun IsIndexable.toReleasedReferenceStorageByteArray(): ByteArray = when (this) {
    is Multiple -> releasedCompositeReference(references)
    is AnyOf -> releasedCompositeReference(references)
    is Reversed<*> -> releasedReference(reference.toReleasedStorageByteArray())
    is ReferenceToMax<*> -> releasedReference(reference.toReleasedStorageByteArray())
    is Normalize -> releasedReference(reference.toReleasedReferenceStorageByteArray())
    is LegacyNormalize -> releasedReference(reference.toReleasedReferenceStorageByteArray())
    is Split -> releasedReference(reference.toReleasedReferenceStorageByteArray(), on.index.toByte())
    is GeoHash -> releasedReference(reference.toReleasedReferenceStorageByteArray(), precisionBits.toByte())
    is MapAnyKeyReference<*, *, *> -> releasedFuzzyReference(parentReference)
    is SetAnyValueReference<*, *> -> releasedFuzzyReference(parentReference)
    is SimpleTypedValueReference<*, *, *> -> parentReference?.toReleasedStorageByteArray() ?: byteArrayOf()
    is TypeReference<*, *, *> -> releasedReference(parentReference?.toReleasedStorageByteArray() ?: byteArrayOf())
    is AnyPropertyReference -> releasedReference(toReleasedStorageByteArray())
    else -> referenceStorageByteArray.bytes.copyOf()
}

private fun IsIndexable.releasedCompositeReference(references: List<IsIndexable>): ByteArray = buildList {
    indexKeyPartType.index.writeVarBytes(::add)
    for (reference in references) {
        val bytes = reference.toReleasedReferenceStorageByteArray()
        bytes.size.writeVarBytes(::add)
        bytes.forEach(::add)
    }
}.toByteArray()

private fun IsIndexable.releasedReference(bytes: ByteArray, suffix: Byte? = null): ByteArray = buildList {
    bytes.size.writeVarIntWithExtraInfo(indexKeyPartType.index.toByte(), ::add)
    bytes.forEach(::add)
    suffix?.let(::add)
}.toByteArray()

private fun IsIndexable.releasedFuzzyReference(parent: AnyPropertyReference?): ByteArray {
    val current = referenceStorageByteArray.bytes
    val parentLength = parent?.calculateStorageByteLength() ?: 0
    return (parent?.toReleasedStorageByteArray() ?: byteArrayOf()) + current.copyOfRange(parentLength, current.size)
}
