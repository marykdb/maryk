package maryk
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
                required = true,
                regEx = "ha.*"
        ), TestMarykObject::string)

        val int = add(1, "int", NumberDefinition(
                type = SInt32,
                maxValue = 6
        ), TestMarykObject::int)

        val uint = add(2, "uint", NumberDefinition(
                type = UInt32,
                required = true,
                final = true
        ), TestMarykObject::uint)

        val double = add(3, "double", NumberDefinition(
                type = Float64
        ), TestMarykObject::double)

        val dateTime = add(4, "dateTime", DateTimeDefinition(
        ), TestMarykObject::dateTime)

        val bool = add(5, "bool", BooleanDefinition(
                required = true,
                final = true
        ), TestMarykObject::bool)

        val enum = add(6, "enum", EnumDefinition(
                values = Option.values(),
                required = true,
                final = true
        ), TestMarykObject::enum)

        val list = add(7, "list", ListDefinition(
                valueDefinition = NumberDefinition(
                        required = true,
                        type = SInt32
                )
        ), TestMarykObject::list)

        val set = add(8, "set", SetDefinition(
                valueDefinition = DateDefinition(required = true)
        ), TestMarykObject::set)

        val map = add(9, "map", MapDefinition(
                keyDefinition = TimeDefinition(required = true),
                valueDefinition = StringDefinition(required = true)
        ), TestMarykObject::map)

        val valueObject = add(10, "valueObject", ValueModelDefinition(
                dataModel = TestValueObject
        ), TestMarykObject::valueObject)

        val subModel = add(11, "subModel", SubModelDefinition(
                dataModel = SubMarykObject
        ), TestMarykObject::subModel)

        val multi = add(12, "multi", MultiTypeDefinition(
                getDefinition = mapOf(
                        0 to StringDefinition(),
                        1 to NumberDefinition(type = SInt32),
                        2 to SubModelDefinition(
                                dataModel = SubMarykObject
                        )
                )::get
        ), TestMarykObject::multi)

        val reference = add(13, "reference", ReferenceDefinition(
                dataModel = SubMarykObject
        ), TestMarykObject::reference)

        val listOfString = add(14, "listOfString", ListDefinition(
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
            properties = Properties
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
        ), SubMarykObject::value)
    }
    companion object: RootDataModel<SubMarykObject>(
            name = "SubMarykObject",
            properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = SubMarykObject(
                map[0] as String
        )
    }
}
