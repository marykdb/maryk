package maryk.test.models

import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.NumberDefinition
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
        val int = add(
            1, "int", NumberDefinition(
                type = SInt32,
                maxValue = 6
            ), TestValueObject::int
        )

        val dateTime = add(2, "dateTime", DateTimeDefinition(), TestValueObject::dateTime)

        val bool = add(3, "bool", BooleanDefinition(), TestValueObject::bool)
    }

    companion object : ValueDataModel<TestValueObject, Properties>(
        name = "TestValueObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<TestValueObject, Properties>) = TestValueObject(
            int = values(1),
            dateTime = values(2),
            bool = values(3)
        )

        override fun equals(other: Any?): Boolean {
            if (other !is ValueDataModel<*, *>) return false

            @Suppress("UNCHECKED_CAST")
            val otherModel = other as ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>

            if (this.name != otherModel.name) return false
            if (this.properties.size != otherModel.properties.size) return false

            return true
        }
    }
}


