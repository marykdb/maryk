package maryk.core.properties.definitions

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Interface to define this is a property definition containing [T]
 */
interface IsPropertyDefinition<T : Any> {
    val required: Boolean
    val final: Boolean

    /**
     * Validates [newValue] against [previousValue] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationException when encountering invalid new value
     */
    fun validateWithRef(
        previousValue: T? = null,
        newValue: T?,
        refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>? = { null }
    ) = when {
        this.final && previousValue != null ->
            throw AlreadySetException(refGetter())
        this.required && newValue == null ->
            throw RequiredException(refGetter())
        else -> Unit
    }

    /** To get embedded properties by [name] */
    fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>?

    /** To get embedded properties by [index] */
    fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>?

    /**
     * Checks if this property definition is compatible with passed definition
     * It is compatible if any property validated by the passed definition
     * is accepted by this definition.
     *
     * Validation rules which are less strict are accepted but more strict or incompatible rules are not.
     */
    fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)? = null
    ): Boolean {
        var compatible = true
        if (!this::class.isInstance(definition)) {
            addIncompatibilityReason?.invoke("Definitions are not of same types: ${this::class.simpleName} vs ${definition::class.simpleName}")
            compatible = false
        }

        if (this.required && !definition.required) {
            addIncompatibilityReason?.invoke("Not required property was made required")
            compatible = false
        }

        return compatible
    }
}
