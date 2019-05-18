package maryk.test.models

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.ObjectValues
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time

data class TestMarykObject(
    val string: String = "haha",
    val int: Int,
    val uint: UInt,
    val double: Double,
    val dateTime: DateTime,
    val bool: Boolean? = null,
    val enum: Option = Option.V1,
    val list: List<Int>? = null,
    val set: Set<Date>? = null,
    val map: Map<Time, String>? = null,
    val valueObject: TestValueObject? = null,
    val embeddedObject: EmbeddedMarykObject? = null,
    val multi: TypedValue<SimpleMarykTypeEnumWithObject<*>, *>? = null,
    val reference: Key<TestMarykModel>? = null,
    val listOfString: List<String>? = null
) {
    object Properties : ObjectPropertyDefinitions<TestMarykObject>() {
        val string = add(
            index = 1u, name = "string",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = TestMarykObject::string
        )

        val int = add(
            index = 2u, name = "int",
            definition = NumberDefinition(
                type = SInt32,
                maxValue = 6
            ),
            getter = TestMarykObject::int
        )

        val uint = add(
            index = 3u, name = "uint",
            definition = NumberDefinition(
                type = UInt32,
                final = true
            ),
            getter = TestMarykObject::uint
        )

        val double = add(
            index = 4u, name = "double",
            definition = NumberDefinition(type = Float64),
            getter = TestMarykObject::double
        )

        val dateTime = add(
            index = 5u, name = "dateTime",
            definition = DateTimeDefinition(),
            getter = TestMarykObject::dateTime
        )

        val bool = add(
            index = 6u, name = "bool",
            definition = BooleanDefinition(
                final = true
            ),
            getter = TestMarykObject::bool
        )

        val enum = add(
            index = 7u, name = "enum",
            definition = EnumDefinition(
                enum = Option,
                default = Option.V1,
                final = true
            ),
            getter = TestMarykObject::enum
        )

        val list = add(
            index = 8u, name = "list",
            definition = ListDefinition(
                required = false,
                valueDefinition = NumberDefinition(
                    type = SInt32
                )
            ),
            getter = TestMarykObject::list
        )

        val set = add(
            index = 9u, name = "set",
            definition = SetDefinition(
                required = false,
                valueDefinition = DateDefinition()
            ),
            getter = TestMarykObject::set
        )

        val map = add(
            index = 10u, name = "map",
            definition = MapDefinition(
                required = false,
                keyDefinition = TimeDefinition(),
                valueDefinition = StringDefinition()
            ),
            getter = TestMarykObject::map
        )

        val valueObject = add(
            index = 11u, name = "valueObject",
            definition = ValueModelDefinition(
                required = false,
                dataModel = TestValueObject
            ),
            getter = TestMarykObject::valueObject
        )

        val embeddedObject = add(
            index = 12u, name = "embeddedObject",
            definition = EmbeddedObjectDefinition(
                required = false,
                dataModel = { EmbeddedMarykObject }
            ),
            getter = TestMarykObject::embeddedObject
        )

        val multi = add(
            index = 13u, name = "multi",
            definition = MultiTypeDefinition(
                required = false,
                typeEnum = SimpleMarykTypeEnumWithObject
            ),
            getter = TestMarykObject::multi
        )

        val reference = add(
            index = 14u, name = "reference",
            definition = ReferenceDefinition(
                required = false,
                dataModel = { TestMarykModel }
            ),
            getter = TestMarykObject::reference
        )

        val listOfString = add(
            index = 15u, name = "listOfString",
            definition = ListDefinition(
                required = false,
                valueDefinition = StringDefinition()
            ),
            getter = TestMarykObject::listOfString
        )
    }

    companion object : ObjectDataModel<TestMarykObject, Properties>(
        name = "TestMarykObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<TestMarykObject, Properties>) = TestMarykObject(
            string = values(1u),
            int = values(2u),
            uint = values(3u),
            double = values(4u),
            dateTime = values(5u),
            bool = values(6u),
            enum = values(7u),
            list = values(8u),
            set = values(9u),
            map = values(10u),
            valueObject = values(11u),
            embeddedObject = values(12u),
            multi = values(13u),
            reference = values(14u),
            listOfString = values(15u)
        )

        override fun equals(other: Any?) =
            other is ObjectDataModel<*, *> &&
                this.name == other.name &&
                this.properties.size == other.properties.size
    }
}

