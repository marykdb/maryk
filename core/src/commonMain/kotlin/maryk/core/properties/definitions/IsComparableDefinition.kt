package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference

/**
 * Property Definition to define comparable properties of type [T] with context [CX].
 * This is used for simple single value properties and not for lists and maps.
 */
interface IsComparableDefinition<T : Comparable<T>, in CX : IsPropertyContext> : IsSimpleValueDefinition<T, CX> {
    val unique: Boolean
    val minValue: T?
    val maxValue: T?

    /**
     * Validate [newValue] against [previousValue] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationException thrown if property is invalid
     */
    override fun validateWithRef(
        previousValue: T?,
        newValue: T?,
        refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?
    ) {
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

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super.compatibleWith(definition, addIncompatibilityReason)

        (definition as? IsComparableDefinition<*, *>)?.let {
            if (definition.unique != this.unique) {
                addIncompatibilityReason?.invoke("Unique cannot be made non unique and other way around")
                compatible = false
            }

            @Suppress("UNCHECKED_CAST")
            if (this.maxValue != null && (definition.maxValue == null || (this.maxValue!!::class == definition.maxValue!!::class && this.maxValue!! < definition.maxValue as T))) {
                addIncompatibilityReason?.invoke("Maximum value cannot be lower than original")
                compatible = false
            }

            @Suppress("UNCHECKED_CAST")
            if (this.minValue != null && (definition.minValue == null || (this.minValue!!::class == definition.minValue!!::class && this.minValue!! > definition.minValue as T))) {
                addIncompatibilityReason?.invoke("Minimum value cannot be higher than original")
                compatible = false
            }
        }

        return compatible
    }
}
