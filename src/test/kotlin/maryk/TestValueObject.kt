package maryk

import maryk.core.objects.Def
import maryk.core.objects.ValueDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.Int32

data class TestValueObject(
        val int: Int,
        val dateTime: DateTime,
        val bool: Boolean
) : ValueDataObject(createBytes(int, dateTime, bool)) {
    object Properties {
        val int = NumberDefinition(
                name = "int",
                type = Int32,
                index = 0,
                maxValue = 6
        )
        val dateTime = DateTimeDefinition(
                name = "dateTime",
                index = 1
        )
        val bool = BooleanDefinition(
                name = "bool",
                index = 2
        )
    }

    companion object: ValueDataModel<TestValueObject>(
            definitions = listOf(
                    Def(Properties.int, TestValueObject::int),
                    Def(Properties.dateTime, TestValueObject::dateTime),
                    Def(Properties.bool, TestValueObject::bool)
            )
    ) {
        override fun construct(values: Map<Int, Any>) = TestValueObject(
            int = values[0] as Int,
            dateTime = values[1] as DateTime,
            bool = values[2] as Boolean
        )
    }
}


