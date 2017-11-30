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
    object Properties {
        val string = StringDefinition(
                name = "string",
                required = true,
                regEx = "ha.*",
                index = 0
        )
        val int = NumberDefinition(
                name = "int",
                type = SInt32,
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
        val list = ListDefinition(
                name = "list",
                index = 7,
                valueDefinition = NumberDefinition(
                        required = true,
                        type = SInt32
                )
        )
        val set = SetDefinition(
                name = "set",
                index = 8,
                valueDefinition = DateDefinition(required = true)
        )
        val map = MapDefinition(
                name = "map",
                index = 9,
                keyDefinition = TimeDefinition(required = true),
                valueDefinition = StringDefinition(required = true)
        )
        val valueObject = ValueModelDefinition(
                name = "valueObject",
                index = 10,
                dataModel = TestValueObject
        )
        val subModel = SubModelDefinition(
                name = "subModel",
                index = 11,
                dataModel = SubMarykObject
        )
        val multi = MultiTypeDefinition(
                name = "multi",
                index = 12,
                getDefinition = mapOf(
                        0 to StringDefinition(),
                        1 to NumberDefinition(type = SInt32),
                        2 to SubModelDefinition(
                                dataModel = SubMarykObject
                        )
                )::get
        )
        val reference = ReferenceDefinition(
                name = "reference",
                index = 13,
                dataModel = SubMarykObject
        )
        val listOfString = ListDefinition(
                name = "listOfString",
                index = 14,
                valueDefinition = StringDefinition(required = true)
        )
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
    object Properties {
        val value = StringDefinition(
                name = "value",
                index = 0
        )
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
