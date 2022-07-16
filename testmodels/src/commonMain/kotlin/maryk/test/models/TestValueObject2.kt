package maryk.test.models

import kotlinx.datetime.LocalDateTime
import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues

data class TestValueObject2(
    val int: Int,
    val dateTime: LocalDateTime,
) : ValueDataObject(toBytes(int, dateTime)) {
    object Properties : ObjectPropertyDefinitions<TestValueObject2>() {
        val int by number(
            1u,
            TestValueObject2::int,
            type = SInt32,
            maxValue = 6
        )

        val dateTime by dateTime(2u, TestValueObject2::dateTime)
    }

    companion object : ValueDataModel<TestValueObject2, Properties>(
        name = "TestValueObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<TestValueObject2, Properties>) = TestValueObject2(
            int = values(1u),
            dateTime = values(2u)
        )
    }
}
