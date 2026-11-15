package maryk.datastore.shared

import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange

/** Only proven independent built-in indexes can bypass old/new value reads. */
fun List<IsIndexable>.indexesToReconcile(
    changes: List<IsChange>,
    references: Collection<AnyPropertyReference>,
    isDeleted: Boolean,
): List<IsIndexable> {
    if (isDeleted) return this
    val changedReferences = if (changes.all { it is Change }) references else {
        val combined = ArrayList<AnyPropertyReference>(references.size)
        combined.addAll(references)
        for (change in changes) when (change) {
            is Change -> Unit
            is ListChange -> change.listValueChanges.forEach { combined.add(it.reference) }
            is SetChange -> change.setValueChanges.forEach { combined.add(it.reference) }
            else -> return this
        }
        combined
    }
    if (changedReferences.any { !it.isSupportedRootChange() }) return this
    return filterNot { it.isIndependentOf(changedReferences) }
}

private fun AnyPropertyReference.isSupportedRootChange(): Boolean {
    if ((this as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference != null) return false
    return when (this::class) {
        ValueWithFixedBytesPropertyReference::class, ValueWithFlexBytesPropertyReference::class ->
            comparablePropertyDefinition is IsSimpleValueDefinition<*, *>
        ListReference::class, MapReference::class, SetReference::class -> true
        else -> false
    }
}

private fun IsIndexable.isIndependentOf(references: Collection<AnyPropertyReference>): Boolean = when {
    this is Multiple -> this.references.all { it.isIndependentOf(references) }
    this::class == ValueWithFixedBytesPropertyReference::class ->
        (this as ValueWithFixedBytesPropertyReference<*, *, *, *>).parentReference == null &&
            references.none { it == this }
    this::class == ValueWithFlexBytesPropertyReference::class ->
        (this as ValueWithFlexBytesPropertyReference<*, *, *, *>).parentReference == null &&
            references.none { it == this }
    else -> false
}
