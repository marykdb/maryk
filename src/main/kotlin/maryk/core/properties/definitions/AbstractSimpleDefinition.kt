package maryk.core.properties.definitions

import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
abstract class AbstractSimpleDefinition<T: Comparable<T>>(
        name: String?,
        index: Short,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        val unique: Boolean,
        val minValue: T?,
        val maxValue: T?
) : AbstractValueDefinition<T>(
        name, index, indexed, searchable, required, final
) {
    /**
     * Validate the contents of the native type
     * @param newValue to validate
     * @throws PropertyValidationException thrown if property is invalid
     */
    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: T?, newValue: T?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        when {
            newValue != null -> {
                when {
                    this.minValue != null && newValue.compareTo(this.minValue) < 0
                            -> throw PropertyOutOfRangeException(
                                this.getRef(parentRefFactory), newValue, this.minValue, this.maxValue
                            )
                    this.maxValue != null && newValue.compareTo(this.maxValue) > 0
                            -> throw PropertyOutOfRangeException(
                                this.getRef(parentRefFactory), newValue, this.minValue, this.maxValue
                            )
                }
            }
        }
    }
}