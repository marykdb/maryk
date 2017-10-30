package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WireType

/**
 * Abstract Property Definition to define numeric properties.
 *
 * Implements methods usefull for numeric definitions
 * @param <T> Type of comparable properties contain
 */
abstract class AbstractNumericDefinition<T: Comparable<T>>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        wireType: WireType,
        unique: Boolean,
        minValue: T?,
        maxValue: T?,
        val random:Boolean
) : AbstractSimpleDefinition<T, IsPropertyContext>(
        name, index, indexed, searchable, required, final, wireType, unique, minValue, maxValue
) {
    /** @return random value */
    abstract fun createRandom(): T
}