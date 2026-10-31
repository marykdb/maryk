package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypedValueReference

/** Preserve the relationship between a concrete qualifier and the payload stored below it. */
fun requireWritableValueReference(
    reference: AnyPropertyReference,
    value: Any?,
    hasValue: (AnyPropertyReference) -> Boolean,
) {
    if (reference is SimpleTypedValueReference<*, *, *> ||
        (reference is TypedValueReference<*, *, *> && reference.comparablePropertyDefinition is IsSimpleValueDefinition<*, *>)) {
        throw RequestException("Change scalar typed values through the multi type parent.")
    }
    if (reference is TypedValueReference<*, *, *> && value == null) {
        throw RequestException("Delete typed values through the multi type parent.")
    }
    if (reference is SetItemReference<*, *> && value != null) {
        @Suppress("UNCHECKED_CAST")
        val item = reference as SetItemReference<Any, IsPropertyContext>
        if (!hasValue(reference) || !areSameStoredScalarValues(item.comparablePropertyDefinition, item.value, value)) {
            throw RequestException("Use SetChange to add set items. An existing item cannot be assigned a different value.")
        }
    }
}

/** A descendant write cannot change a final value that is already present. */
fun findStoredFinalParent(
    reference: AnyPropertyReference,
    hasValue: (AnyPropertyReference) -> Boolean,
): AnyPropertyReference? = storageReferenceParents(reference).firstOrNull {
    it.comparablePropertyDefinition.final && hasValue(it)
}

/** Writes may create their target, but must preserve all structural ancestor markers. */
fun requireStoredReferenceParents(
    reference: AnyPropertyReference,
    hasValue: (AnyPropertyReference) -> Boolean,
    readCount: (AnyPropertyReference) -> Int?,
    allowMissingMapParent: Boolean = false,
) {
    val parents = storageReferenceParents(reference)
    for (item in (listOf(reference) + parents).filterIsInstance<ListItemReference<*, *>>()) {
        val count = item.parentReference?.let(readCount)
        if (count == null || item.index >= count.toUInt()) {
            throw RequestException("Property '$reference' belongs to a missing list item")
        }
    }
    for (parent in parents) {
        val canInitializeMap = allowMissingMapParent && parent === parents.firstOrNull() && parent is IsMapReference<*, *, *, *>
        if (!canInitializeMap && requiresStorageParentMarker(parent) && !hasValue(parent)) {
            throw RequestException("Property '$reference' requires parent '$parent' to exist")
        }
    }
}
