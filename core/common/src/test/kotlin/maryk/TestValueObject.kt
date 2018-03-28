package maryk

import maryk.core.objects.ValueDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32

data class TestValueObject(
    val int: Int,
    val dateTime: DateTime,
    val bool: Boolean
) : ValueDataObject(toBytes(int, dateTime, bool)) {
    object Properties : PropertyDefinitions<TestValueObject>() {
        val int = add(0, "int", NumberDefinition(
            type = SInt32,
            maxValue = 6
        ), TestValueObject::int)

        val dateTime = add(1, "dateTime", DateTimeDefinition(), TestValueObject::dateTime)

        val bool = add(2, "bool", BooleanDefinition(), TestValueObject::bool)
    }

    companion object: ValueDataModel<TestValueObject, Properties>(
        name = "TestValueObject",
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = TestValueObject(
            int = map[0] as Int,
            dateTime = map[1] as DateTime,
            bool = map[2] as Boolean
        )
    }
}


