package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
interface IsSimpleDefinition<T: Comparable<T>, in CX: IsPropertyContext> : IsSimpleValueDefinition<T, CX> {
    val unique: Boolean
    val minValue: T?
    val maxValue: T?

    /**
     * Validate the contents of the native type
     * @param newValue to validate
     * @throws ValidationException thrown if property is invalid
     */
    override fun validateWithRef(previousValue: T?, newValue: T?, refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)
        when {
            newValue != null -> {
                when {
                    this.minValue != null && newValue < this.minValue!!
                            -> throw OutOfRangeException(
                                    refGetter(), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                            )
                    this.maxValue != null && newValue > this.maxValue!!
                            -> throw OutOfRangeException(
                                    refGetter(), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                            )
                }
            }
        }
    }
}