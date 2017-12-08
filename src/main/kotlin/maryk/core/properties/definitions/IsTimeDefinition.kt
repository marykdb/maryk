package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.IsTime
import maryk.core.properties.types.TimePrecision

/** Time Property Definition to define time properties with precision.
 * @param <T> Comparable type defining a time
 */
interface IsTimeDefinition<T : IsTime<T>> : IsMomentDefinition<T>, IsSerializableFixedBytesEncodable<T, IsPropertyContext> {
    val precision: TimePrecision

    override fun calculateStorageByteLength(value: T) = this.byteSize

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)
}