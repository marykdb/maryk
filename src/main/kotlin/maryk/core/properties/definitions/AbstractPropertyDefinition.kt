package maryk.core.properties.definitions

import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.references.IsPropertyReference

/**
 * Abstract Property Definition to define properties
 * @param <T> Type defined by definition
 */
abstract class AbstractPropertyDefinition<T: Any>  (
        val indexed: Boolean,
        val searchable: Boolean,
        val required: Boolean,
        val final: Boolean
) : IsPropertyDefinition<T> {
    override fun validateWithRef(previousValue: T?, newValue: T?, refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) = when {
        this.final && previousValue != null -> throw AlreadySetException(refGetter())
        this.required && newValue == null -> throw RequiredException(refGetter())
        else -> {}
    }
}