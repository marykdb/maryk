package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.TimePrecision
import maryk.lib.time.IsTime

/** Time Property Definition to define time properties of [T] with precision. */
interface IsTimeDefinition<T : IsTime<T>> : IsMomentDefinition<T>, IsSerializableFixedBytesEncodable<T, IsPropertyContext> {
    val precision: TimePrecision

    override fun calculateStorageByteLength(value: T) = this.byteSize

    companion object {
        internal fun <DO : Any> addPrecision(definitions: PropertyDefinitions<DO>, getter: (DO) -> TimePrecision) {
            definitions.add(8, "precision", EnumDefinition(values = TimePrecision.values()), getter)
        }
    }
}
