package maryk.core.properties.references

/** Interface to declare this is an embedded property reference like a map key/value or list value
 * @param T Type of property contained
 */
interface HasEmbeddedPropertyReference<T> {
    /** Get an embedded ref by reader
     * @param reader to read sub bytes with
     */
    fun getEmbeddedRef(reader: () -> Byte) : IsPropertyReference<*, *>

    /** Get an embedded ref by name
     * @param name to get reference for
     */
    fun getEmbedded(name: String): IsPropertyReference<*, *>
}