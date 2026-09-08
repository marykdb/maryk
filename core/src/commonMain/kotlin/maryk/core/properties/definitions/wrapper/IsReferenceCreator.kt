package maryk.core.properties.definitions.wrapper

import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/** A property capable of creating its concrete reference type. */
fun interface IsReferenceCreator<out R : IsPropertyReference<*, *, *>> {
    fun ref(parentRef: AnyPropertyReference?): R
}
