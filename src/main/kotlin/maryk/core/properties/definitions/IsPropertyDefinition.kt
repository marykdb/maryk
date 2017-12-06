package maryk.core.properties.definitions

import maryk.core.properties.definitions.wrapper.IsDataObjectProperty
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Interface to define this is a property definition
 * @param <T> Type of Property contained in the definition
 */
interface IsPropertyDefinition<T: Any> {
    /**
     * Validates the values on propertyDefinition
     * @param previousValue previous value for validation
     * @param newValue      new value for validation
     * @param refGetter      create a ref for the current level
     * @throws ValidationException when encountering invalid new value
     */
    fun validateWithRef(previousValue: T? = null, newValue: T?, refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>>? = { null })

    /** To get embedded properties by name
     * @param name to fetch property of
     */
    fun getEmbeddedByName(name: String): IsDataObjectProperty<*, *, *>?

    /** To get embedded properties by index
     * @param index to fetch property of
     */
    fun getEmbeddedByIndex(index: Int): IsDataObjectProperty<*, *, *>?
}