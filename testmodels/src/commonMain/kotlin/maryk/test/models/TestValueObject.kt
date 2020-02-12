package maryk.test.models

import maryk.core.models.ValueDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues
import maryk.lib.time.DateTime

data class TestValueObject(
    val int: Int,
    val dateTime: DateTime,
    val bool: Boolean
) : ValueDataObject(toBytes(int, dateTime, bool)) {
    object Properties : ObjectPropertyDefinitions<TestValueObject>() {
        val int by number(
            1u,
            TestValueObject::int,
            type = SInt32,
            maxValue = 6
        )

        val dateTime by dateTime(2u, TestValueObject::dateTime)

        val bool by boolean(3u, TestValueObject::bool)
    }

    companion object : ValueDataModel<TestValueObject, Properties>(
        name = "TestValueObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<TestValueObject, Properties>) = TestValueObject(
            int = values(1u),
            dateTime = values(2u),
            bool = values(3u)
        )

        override fun equals(other: Any?) =
            other is ValueDataModel<*, *> &&
                this.name == other.name &&
                this.properties.size == (other.properties as AbstractPropertyDefinitions<*>).size
    }
}


