package maryk.core.properties.definitions

import maryk.core.properties.types.IsTime
import maryk.core.properties.types.TimePrecision

/**
 * Abstract Time Property Definition to define time properties with precision.
 * @param <T> Comparable type defining a time
 */
abstract class AbstractTimeDefinition<T : IsTime<T>>(
        name: String?,
        index: Short,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        unique: Boolean,
        minValue: T?,
        maxValue: T?,
        fillWithNow: Boolean,
        val precision: TimePrecision
) : AbstractMomentDefinition<T>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue, fillWithNow
) {
    override fun convertToBytes(value: T, bytes: ByteArray?, offset: Int) = value.toBytes(precision, bytes, offset)

    override fun convertToBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, reserver, writer)
}