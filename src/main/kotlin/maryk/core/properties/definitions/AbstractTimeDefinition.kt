package maryk.core.properties.definitions

import maryk.core.properties.types.IsTime
import maryk.core.properties.types.TimePrecision
import maryk.core.protobuf.WireType

/**
 * Abstract Time Property Definition to define time properties with precision.
 * @param <T> Comparable type defining a time
 */
abstract class AbstractTimeDefinition<T : IsTime<T>>(
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
        fillWithNow: Boolean,
        val precision: TimePrecision
) : AbstractMomentDefinition<T>(
        name, index, indexed, searchable, required, final, wireType, unique, minValue, maxValue, fillWithNow
) {
    override fun convertToStorageBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, reserver, writer)
}