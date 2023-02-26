package maryk.test.models

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.ValueModel
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues

data class TestValueObject2(
    val int: Int,
    val dateTime: LocalDateTime,
) : ValueDataObject(toBytes(int, dateTime)) {
    companion object : ValueModel<TestValueObject2, Companion>(TestValueObject2::class) {
        val int by number(
            1u,
            TestValueObject2::int,
            type = SInt32,
            maxValue = 6
        )

        val dateTime by dateTime(2u, TestValueObject2::dateTime)
        override fun invoke(values: ObjectValues<TestValueObject2, Companion>) = TestValueObject2(
            int = values(1u),
            dateTime = values(2u)
        )
    }
}
