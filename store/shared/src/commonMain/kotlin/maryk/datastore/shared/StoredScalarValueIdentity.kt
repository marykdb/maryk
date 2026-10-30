package maryk.datastore.shared

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable

/** Compare equal scalar values using their ordinary storage codec, preserving raw NaN payloads. */
fun <T : Any> areSameStoredScalarValues(definition: IsPropertyDefinition<T>, left: T?, right: T?): Boolean {
    if (left != right) return false
    if (left == null || right == null) return true

    @Suppress("UNCHECKED_CAST")
    val storageDefinition = definition as? IsStorageBytesEncodable<T> ?: return true
    return storageDefinition.toStorageBytes(left).contentEquals(storageDefinition.toStorageBytes(right))
}
