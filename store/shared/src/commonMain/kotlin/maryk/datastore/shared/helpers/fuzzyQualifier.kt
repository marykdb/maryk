package maryk.datastore.shared.helpers

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.datastore.shared.isSkippableDataError
import maryk.datastore.shared.rethrowIfFatal

/** Combine a record key with a matched property qualifier. */
fun matchedQualifier(
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    referenceBytes: ByteArray,
    refOffset: Int,
    refLength: Int
): ByteArray {
    val qualifier = ByteArray(keyLength + refLength)
    key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
    referenceBytes.copyInto(qualifier, keyLength, refOffset, refOffset + refLength)
    return qualifier
}

/**
 * Decode one wildcard map key or set element from a matched qualifier.
 * A wildcard reference resolves to a list, but its qualifier encodes a single scalar value.
 */
fun readFuzzyValue(
    reference: IsPropertyReference<*, *, *>,
    referenceBytes: ByteArray,
    refOffset: Int,
    refLength: Int
): Any? = when (reference) {
    is MapAnyKeyReference<*, *, *> -> readFuzzyValue(reference, referenceBytes, refOffset, refLength)
    is SetAnyValueReference<*, *> -> readFuzzyValue(reference, referenceBytes, refOffset, refLength)
    else -> null
}

/** Decode one key, rather than the list resolved by a wildcard map reference. */
fun <K : Any> readFuzzyValue(
    reference: MapAnyKeyReference<K, *, *>,
    referenceBytes: ByteArray,
    refOffset: Int,
    refLength: Int
): K? = readFuzzyScalarValue(reference, referenceBytes, refOffset, refLength)

/** Decode one element, rather than the list resolved by a wildcard set reference. */
fun <T : Any> readFuzzyValue(
    reference: SetAnyValueReference<T, *>,
    referenceBytes: ByteArray,
    refOffset: Int,
    refLength: Int
): T? = readFuzzyScalarValue(reference, referenceBytes, refOffset, refLength)

private fun <T : Any> readFuzzyScalarValue(
    reference: IsIndexablePropertyReference<T>,
    referenceBytes: ByteArray,
    refOffset: Int,
    refLength: Int
): T? {
    val refEnd = refOffset + refLength

    return try {
        val parentLength = reference.toQualifierStorageByteArray()?.size ?: 0
        var readIndex = refOffset + parentLength
        if (readIndex >= refEnd) return null

        val valueLength = initIntByVar { referenceBytes[readIndex++] }
        if (readIndex + valueLength > refEnd) return null

        reference.readStorageBytes(valueLength) { referenceBytes[readIndex++] }
    } catch (error: Throwable) {
        error.rethrowIfFatal()
        if (error.isSkippableDataError()) null else throw error
    }
}
