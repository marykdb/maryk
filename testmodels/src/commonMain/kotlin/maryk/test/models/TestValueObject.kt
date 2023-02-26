package maryk.test.models

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.ValueModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues

data class TestValueObject(
    val int: Int,
    val dateTime: LocalDateTime,
    val bool: Boolean
) : ValueDataObject(toBytes(int, dateTime, bool)) {
    companion object : ValueModel<TestValueObject, Companion>(TestValueObject::class) {
        val int by number(
            1u,
            TestValueObject::int,
            type = SInt32,
            maxValue = 6
        )

        val dateTime by dateTime(2u, TestValueObject::dateTime)

        val bool by boolean(3u, TestValueObject::bool)

        override fun invoke(values: ObjectValues<TestValueObject, Companion>) =
            TestValueObject(
                int = values(1u),
                dateTime = values(2u),
                bool = values(3u)
            )
    }
}
