package maryk.datastore.shared.encryption

import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.LegacyNormalize
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.definitions.index.UUIDv7Key
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference

/** Conservatively detect sensitive data dependencies without changing query-planner matching. */
fun IsIndexable.mayContainPropertyReference(propertyReference: AnyPropertyReference): Boolean = when (this) {
    is AnyOf -> references.any { it.mayContainPropertyReference(propertyReference) }
    is Multiple -> references.any { it.mayContainPropertyReference(propertyReference) }
    is Normalize -> reference.mayContainPropertyReference(propertyReference)
    is LegacyNormalize -> reference.mayContainPropertyReference(propertyReference)
    is Split -> reference.mayContainPropertyReference(propertyReference)
    is GeoHash -> reference.mayContainPropertyReference(propertyReference)
    is Reversed<*> -> reference.mayContainPropertyReference(propertyReference)
    is ReferenceToMax<*> -> reference.mayContainPropertyReference(propertyReference)
    is IsIndexablePropertyReference<*> -> isForPropertyReference(propertyReference)
    UUIDv4Key, UUIDv7Key -> false
    // Custom index implementations expose no dependency contract. Do not assume they are safe.
    else -> true
}
