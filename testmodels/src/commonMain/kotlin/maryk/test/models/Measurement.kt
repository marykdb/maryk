package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.core.properties.types.numeric.UInt16
import maryk.core.values.Values
import maryk.test.models.Measurement.timestamp

object WeightMeasurement : DataModel<WeightMeasurement>() {
    val weightInKg by number(index = 1u, type = UInt16)
}
object LengthMeasurement : DataModel<WeightMeasurement>() {
    val lengthInCm by number(index = 1u, type = UInt16)
}

sealed class MeasurementType<T: Any>(index: UInt, override val definition: IsUsableInMultiType<T, *>?, alternativeNames: Set<String>? = null) : IndexedEnumImpl<MeasurementType<Any>>(index, alternativeNames),
    MultiTypeEnum<T> {
    object Weight : MeasurementType<Values<WeightMeasurement>>(1u, EmbeddedValuesDefinition(dataModel = { WeightMeasurement }))
    object Length : MeasurementType<Values<LengthMeasurement>>(2u, EmbeddedValuesDefinition(dataModel = { LengthMeasurement }))
    object Number : MeasurementType<UShort>(3u, NumberDefinition(type = UInt16))

    companion object : MultiTypeEnumDefinition<MeasurementType<out Any>>(
        MeasurementType::class,
        values = { listOf(Weight, Length, Number) },
    )
}

object Measurement : RootDataModel<Measurement>(
    keyDefinition = {
        Multiple(
            Reversed(timestamp.ref()),
        )
    },
    indices = {
        listOf(
            Measurement { measurement.withType(MeasurementType.Length) { lengthInCm::ref } },
            Measurement { measurement simpleRefAtType MeasurementType.Number }
        )
    },
) {
    val timestamp by dateTime(index = 1u, final = true, precision = MILLIS)
    val measurement by multiType(index = 2u, typeEnum = MeasurementType, final = true)
}
