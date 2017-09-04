package maryk
import maryk.core.objects.Def
import maryk.core.objects.RootDataModel
import maryk.core.objects.definitions
import maryk.core.properties.definitions.*
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.Int32
import maryk.core.properties.types.numeric.UInt32

enum class Option(
        override val index: Int
): IndexedEnum<Option> {
    V0(0), V1(1), V2(2)
}

data class TestMarykObject(
        val string: String = "haha",
        val int: Int,
        val uint: UInt32,
        val double: Double,
        val dateTime: DateTime,
        val bool: Boolean? = null,
        val enum: Option = Option.V0
) {
    constructor(values: Map<Int, *>) : this(
            string = values[0] as String,
            int = values[1] as Int,
            uint = values[2] as UInt32,
            double = values[3] as Double,
            dateTime = values[4] as DateTime,
            bool = values[5] as Boolean?,
            enum = values[6] as Option
    )

    object Properties {
        val string = StringDefinition(
                name = "string",
                required = true,
                regEx = "ha.*",
                index = 0
        )
        val int = NumberDefinition(
                name = "int",
                type = Int32,
                index = 1,
                maxValue = 6
        )
        val uint = NumberDefinition(
                name = "uint",
                type = UInt32,
                index = 2,
                required = true,
                final = true
        )
        val double = NumberDefinition(
                name = "double",
                type = Float64,
                index = 3
        )
        val dateTime = DateTimeDefinition(
                name = "dateTime",
                index = 4
        )
        val bool = BooleanDefinition(
                name = "bool",
                index = 5,
                required = true,
                final = true
        )
        val enum = EnumDefinition(
                name = "enum",
                index = 6,
                values = Option.values(),
                required = true,
                final = true
        )
    }

    companion object: RootDataModel<TestMarykObject>(
            keyDefinitions = definitions(
                    Properties.uint,
                    Properties.bool,
                    Properties.enum
            ),
            definitions = listOf(
                    Def(Properties.string, TestMarykObject::string),
                    Def(Properties.int, TestMarykObject::int),
                    Def(Properties.uint, TestMarykObject::uint),
                    Def(Properties.double, TestMarykObject::double),
                    Def(Properties.bool, TestMarykObject::bool),
                    Def(Properties.dateTime, TestMarykObject::dateTime),
                    Def(Properties.enum, TestMarykObject::enum)
            )
    )
}