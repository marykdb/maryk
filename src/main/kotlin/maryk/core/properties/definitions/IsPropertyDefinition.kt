package maryk.core.properties.definitions

import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Interface to define this is a property definition
 * @param <T> Type of Property contained in the definition
 */
interface IsPropertyDefinition<T: Any> {
    val indexed: Boolean
    val searchable: Boolean
    val required: Boolean
    val final: Boolean

    /**
     * Validates the values on propertyDefinition
     * @param previousValue previous value for validation
     * @param newValue      new value for validation
     * @param refGetter      create a ref for the current level
     * @throws ValidationException when encountering invalid new value
     */
    fun validateWithRef(previousValue: T? = null, newValue: T?, refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>>? = { null }) = when {
        this.final && previousValue != null -> throw AlreadySetException(refGetter())
        this.required && newValue == null -> throw RequiredException(refGetter())
        else -> {}
    }

    /** To get embedded properties by name
     * @param name to fetch property of
     */
    fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>?

    /** To get embedded properties by index
     * @param index to fetch property of
     */
    fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>?
}