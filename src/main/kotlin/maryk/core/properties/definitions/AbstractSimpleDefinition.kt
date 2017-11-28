package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
abstract class AbstractSimpleDefinition<T: Comparable<T>, CX: IsPropertyContext>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        wireType: WireType,
        val unique: Boolean,
        val minValue: T?,
        val maxValue: T?
) : AbstractSimpleValueDefinition<T, CX>(
        name, index, indexed, searchable, required, final, wireType
) {
    /**
     * Validate the contents of the native type
     * @param newValue to validate
     * @throws ValidationException thrown if property is invalid
     */
    override fun validate(previousValue: T?, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        when {
            newValue != null -> {
                when {
                    this.minValue != null && newValue.compareTo(this.minValue) < 0
                            -> throw OutOfRangeException(
                                this.getRef(parentRefFactory), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                            )
                    this.maxValue != null && newValue.compareTo(this.maxValue) > 0
                            -> throw OutOfRangeException(
                                this.getRef(parentRefFactory), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                            )
                }
            }
        }
    }
}