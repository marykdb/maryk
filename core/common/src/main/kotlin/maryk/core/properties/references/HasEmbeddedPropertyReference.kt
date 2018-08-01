package maryk.core.properties.references

/** Interface to declare this is an embedded property reference of type [T] like a map key/value or list value */
interface HasEmbeddedPropertyReference<T> {
    /** Get an embedded ref by [reader] */
    fun getEmbeddedRef(reader: () -> Byte) : AnyPropertyReference

    /** Get an embedded ref by [name] */
    fun getEmbedded(name: String): AnyPropertyReference
}
