package maryk.core.properties.definitions.index

import maryk.core.properties.references.AnyPropertyReference

fun IsIndexable.hasNormalizeIndex(reference: AnyPropertyReference): Boolean = when (this) {
    is Normalize -> this.isForPropertyReference(reference)
    is Multiple -> this.references.any { it.hasNormalizeIndex(reference) }
    else -> false
}
