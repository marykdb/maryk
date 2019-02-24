package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext

/** Interface to declare this is an embedded property reference of type [T] like a map key/value or list value */
interface HasEmbeddedPropertyReference<T> {
    /** Get an embedded ref by [reader] and [context] */
    fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference

    /** Get an embedded storage ref by [reader] and [context] */
    fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: CompleteReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference

    /** Get an embedded ref by [name] and [context] */
    fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference
}
