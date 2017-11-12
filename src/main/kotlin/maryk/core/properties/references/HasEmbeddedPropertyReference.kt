package maryk.core.properties.references

/** Interface to declare this is an embedded property reference like a map key/value or list value
 * @param <T> Type of property contained
 */
interface HasEmbeddedPropertyReference<T> {
    /** Get an embedded ref by index
     * @param reader to read sub bytes with
     */
    fun getEmbeddedRef(reader: () -> Byte) : IsPropertyReference<*, *>
}