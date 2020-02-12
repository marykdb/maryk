package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.TimePrecision
import maryk.lib.time.IsTime

/** Time Property Definition to define time properties of [T] with precision. */
interface IsTimeDefinition<T : IsTime<T>> :
    IsMomentDefinition<T>,
    IsSerializableFixedBytesEncodable<T, IsPropertyContext> {
    val precision: TimePrecision

    override fun calculateStorageByteLength(value: T) = this.byteSize
}

open class TimePrecisionContext : IsPropertyContext {
    var precision: TimePrecision? = null
}
