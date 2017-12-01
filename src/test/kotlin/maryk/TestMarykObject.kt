package maryk
import maryk.core.objects.Def
import maryk.core.objects.RootDataModel
import maryk.core.objects.definitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.types.Date
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.Key
import maryk.core.properties.types.Time
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
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
        val enum: Option = Option.V0,
        val list: List<Int>? = null,
        val set: Set<Date>? = null,
        val map: Map<Time, String>? = null,
        val valueObject: TestValueObject? = null,
        val subModel: SubMarykObject? = null,
        val multi: TypedValue<*>? = null,
        val reference: Key<SubMarykObject>? = null,
        val listOfString: List<String>? = null
) {
    object Properties: PropertyDefinitions<TestMarykObject>() {
        val string = add(0, "string", StringDefinition(
                name = "string",
                required = true,
                regEx = "ha.*",
                index = 0
        ), TestMarykObject::string)

        val int = add(1, "int", NumberDefinition(
                name = "int",
                type = SInt32,
                index = 1,
                maxValue = 6
        ), TestMarykObject::int)

        val uint = add(2, "uint", NumberDefinition(
                name = "uint",
                type = UInt32,
                index = 2,
                required = true,
                final = true
        ), TestMarykObject::uint)

        val double = add(3, "double", NumberDefinition(
                name = "double",
                type = Float64,
                index = 3
        ), TestMarykObject::double)

        val dateTime = add(4, "dateTime", DateTimeDefinition(
                name = "dateTime",
                index = 4
        ), TestMarykObject::dateTime)

        val bool = add(5, "bool", BooleanDefinition(
                name = "bool",
                index = 5,
                required = true,
                final = true
        ), TestMarykObject::bool)

        val enum = add(6, "enum", EnumDefinition(
                name = "enum",
                index = 6,
                values = Option.values(),
                required = true,
                final = true
        ), TestMarykObject::enum)

        val list = add(7, "list", ListDefinition(
                name = "list",
                index = 7,
                valueDefinition = NumberDefinition(
                        required = true,
                        type = SInt32
                )
        ), TestMarykObject::list)

        val set = add(8, "set", SetDefinition(
                name = "set",
                index = 8,
                valueDefinition = DateDefinition(required = true)
        ), TestMarykObject::set)

        val map = add(9, "map", MapDefinition(
                name = "map",
                index = 9,
                keyDefinition = TimeDefinition(required = true),
                valueDefinition = StringDefinition(required = true)
        ), TestMarykObject::map)

        val valueObject = add(10, "valueObject", ValueModelDefinition(
                name = "valueObject",
                index = 10,
                dataModel = TestValueObject
        ), TestMarykObject::valueObject)

        val subModel = add(11, "subModel", SubModelDefinition(
                name = "subModel",
                index = 11,
                dataModel = SubMarykObject
        ), TestMarykObject::subModel)

        val multi = add(12, "multi", MultiTypeDefinition(
                name = "multi",
                index = 12,
                getDefinition = mapOf(
                        0 to StringDefinition(),
                        1 to NumberDefinition(type = SInt32),
                        2 to SubModelDefinition(
                                dataModel = SubMarykObject
                        )
                )::get
        ), TestMarykObject::multi)

        val reference = add(13, "reference", ReferenceDefinition(
                name = "reference",
                index = 13,
                dataModel = SubMarykObject
        ), TestMarykObject::reference)

        val listOfString = add(14, "listOfString", ListDefinition(
                name = "listOfString",
                index = 14,
                valueDefinition = StringDefinition(required = true)
        ), TestMarykObject::listOfString)
    }

    companion object: RootDataModel<TestMarykObject>(
            name = "TestMarykObject",
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
                    Def(Properties.enum, TestMarykObject::enum),
                    Def(Properties.list, TestMarykObject::list),
                    Def(Properties.set, TestMarykObject::set),
                    Def(Properties.map, TestMarykObject::map),
                    Def(Properties.valueObject, TestMarykObject::valueObject),
                    Def(Properties.subModel, TestMarykObject::subModel),
                    Def(Properties.multi, TestMarykObject::multi),
                    Def(Properties.reference, TestMarykObject::reference),
                    Def(Properties.listOfString, TestMarykObject::listOfString)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = TestMarykObject(
                string = map[0] as String,
                int = map[1] as Int,
                uint = map[2] as UInt32,
                double = map[3] as Double,
                dateTime = map[4] as DateTime,
                bool = map[5] as Boolean?,
                enum = map[6] as Option,
                list = map[7] as List<Int>?,
                set = map[8] as Set<Date>?,
                map = map[9] as Map<Time, String>?,
                valueObject = map[10] as TestValueObject?,
                subModel = map[11] as SubMarykObject?,
                multi = map[12] as TypedValue<*>?,
                reference = map[13] as Key<SubMarykObject>?,
                listOfString = map[14] as List<String>?
        )
    }
}

data class SubMarykObject(
        val value: String
){
    object Properties : PropertyDefinitions<SubMarykObject>() {
        val value = add(0, "value", StringDefinition(
                name = "value",
                index = 0
        ), SubMarykObject::value)
    }
    companion object: RootDataModel<SubMarykObject>(
            name = "SubMarykObject",
            definitions = listOf(
                    Def(Properties.value, SubMarykObject::value)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = SubMarykObject(
                map[0] as String
        )
    }
}
