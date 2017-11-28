package maryk.core.properties.definitions

import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Interface to define this is a property definition
 * @param <T> Type of Property contained in the definition
 */
interface IsPropertyDefinition<T: Any> {
    /** The name of the property definition */
    val name: String?

    /** The index position of this property definition */
    val index: Int

    /**
     * Get a reference to this definition
     * @param parentReference reference to parent property if present
     * @return Complete property reference
     */
    fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>? = { null }): IsPropertyReference<T, IsPropertyDefinition<T>>

    /**
     * Validates the values on propertyDefinition
     * @param parentRefFactory     for creating property reference to parent
     * @param previousValue previous value for validation
     * @param newValue      new value for validation
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })

    /** To get embedded properties by name
     * @param name to fetch property of
     */
    fun getEmbeddedByName(name: String): IsPropertyDefinition<out Any>?

    /** To get embedded properties by index
     * @param index to fetch property of
     */
    fun getEmbeddedByIndex(index: Int): IsPropertyDefinition<out Any>?
}