package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/** Property reference with parent */
interface IsPropertyReferenceWithParent<T : Any, out D : IsPropertyDefinition<T>, out P : AnyPropertyReference, V : Any> : IsPropertyReference<T, D, V> {
    val parentReference: P?
}
