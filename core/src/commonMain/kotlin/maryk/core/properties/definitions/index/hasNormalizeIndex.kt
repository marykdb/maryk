package maryk.core.properties.definitions.index

import maryk.core.properties.references.AnyPropertyReference

fun IsIndexable.hasNormalizeIndex(reference: AnyPropertyReference): Boolean = when (this) {
    is Normalize -> this.isForPropertyReference(reference)
    is Split -> this.reference.hasNormalizeIndex(reference)
    is AnyOf -> this.references.any { it.hasNormalizeIndex(reference) }
    is Multiple -> this.references.any { it.hasNormalizeIndex(reference) }
    else -> false
}
