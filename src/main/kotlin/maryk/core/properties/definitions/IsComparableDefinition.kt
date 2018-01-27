package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Property Definition to define comparable properties of type [T] with context [CX].
 *
 * This is used for simple single value properties and not for lists and maps.
 */
interface IsComparableDefinition<T: Comparable<T>, in CX: IsPropertyContext> : IsSimpleValueDefinition<T, CX> {
    val unique: Boolean
    val minValue: T?
    val maxValue: T?

    /**
     * Validate [newValue] against [previousValue] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationException thrown if property is invalid
     */
    override fun validateWithRef(previousValue: T?, newValue: T?, refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)
        when {
            newValue != null -> {
                when {
                    this.minValue?.let {
                        newValue < it
                    } ?: false
                    -> throw OutOfRangeException(
                        refGetter(), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                    )
                    this.maxValue?.let {
                        newValue > it
                    } ?: false
                    -> throw OutOfRangeException(
                        refGetter(), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                    )
                }
            }
        }
    }

    companion object {
        internal fun <DO : Any> addUnique(definitions: PropertyDefinitions<DO>, getter: (DO) -> Boolean) {
            definitions.add(4, "unique", BooleanDefinition(), getter)
        }
    }
}