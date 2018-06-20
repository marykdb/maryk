package maryk
import maryk.core.objects.DataModel
import maryk.core.objects.RootDataModel
import maryk.core.objects.definitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time

enum class Option(
    override val index: Int
): IndexedEnum<Option> {
    V0(0), V1(1), V2(2);

    companion object: IndexedEnumDefinition<Option>("Option", Option::values)
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
    val embeddedObject: EmbeddedMarykObject? = null,
    val multi: TypedValue<Option, *>? = null,
    val reference: Key<TestMarykObject>? = null,
    val listOfString: List<String>? = null,
    val selfReference: Key<TestMarykObject>? = null
) {
    object Properties: PropertyDefinitions<TestMarykObject>() {
        val string = add(
            index = 0, name = "string",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = TestMarykObject::string
        )

        val int = add(
            index = 1, name = "int",
            definition = NumberDefinition(
                type = SInt32,
                maxValue = 6
            ),
            getter = TestMarykObject::int
        )

        val uint = add(
            index = 2, name = "uint",
            definition = NumberDefinition(
                type = UInt32,
                final = true
            ),
            getter = TestMarykObject::uint
        )

        val double = add(
            index = 3, name = "double",
            definition = NumberDefinition(type = Float64),
            getter = TestMarykObject::double
        )

        val dateTime = add(
            index = 4, name = "dateTime",
            definition = DateTimeDefinition(),
            getter = TestMarykObject::dateTime
        )

        val bool = add(
            index = 5, name = "bool",
            definition = BooleanDefinition(
                final = true
            ),
            getter = TestMarykObject::bool
        )

        val enum = add(
            index = 6, name = "enum",
            definition = EnumDefinition(
                enum = Option,
                default = Option.V0,
                final = true
            ),
            getter = TestMarykObject::enum
        )

        val list = add(
            index = 7, name = "list",
            definition = ListDefinition(
                required = false,
                valueDefinition = NumberDefinition(
                    type = SInt32
                )
            ),
            getter = TestMarykObject::list
        )

        val set = add(
            index = 8, name = "set",
            definition = SetDefinition(
                required = false,
                valueDefinition = DateDefinition()
            ),
            getter = TestMarykObject::set
        )

        val map = add(
            index = 9, name = "map",
            definition = MapDefinition(
                required = false,
                keyDefinition = TimeDefinition(),
                valueDefinition = StringDefinition()
            ),
            getter = TestMarykObject::map
        )

        val valueObject = add(
            index = 10, name = "valueObject",
            definition = ValueModelDefinition(
                required = false,
                dataModel = TestValueObject
            ),
            getter = TestMarykObject::valueObject
        )

        val embeddedObject = add(
            index = 11, name = "embeddedObject",
            definition = EmbeddedObjectDefinition(
                required = false,
                dataModel = { EmbeddedMarykObject }
            ),
            getter = TestMarykObject::embeddedObject
        )

        val multi = add(
            index = 12, name = "multi",
            definition = MultiTypeDefinition<Option, IsPropertyContext>(
                required = false,
                typeEnum = Option,
                definitionMap = mapOf(
                    Option.V0 to StringDefinition(),
                    Option.V1 to NumberDefinition(type = SInt32),
                    Option.V2 to EmbeddedObjectDefinition(
                        dataModel = { EmbeddedMarykObject }
                    )
                )
            ),
            getter = TestMarykObject::multi
        )

        val reference = add(
            index = 13, name = "reference",
            definition = ReferenceDefinition(
                required = false,
                dataModel = { TestMarykObject }
            ),
            getter = TestMarykObject::reference
        )

        val listOfString = add(
            index = 14, name = "listOfString",
            definition = ListDefinition(
                required = false,
                valueDefinition = StringDefinition()
            ),
            getter = TestMarykObject::listOfString
        )

        val selfReference = add(
            index = 15, name = "selfReference",
            definition = ReferenceDefinition(
                required = false,
                dataModel = { TestMarykObject }
            ),
            getter = TestMarykObject::selfReference
        )
    }

    companion object: RootDataModel<TestMarykObject, Properties>(
        name = "TestMarykObject",
        keyDefinitions = definitions(
            Properties.uint,
            Properties.bool,
            Properties.enum
        ),
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = TestMarykObject(
            string = map(0),
            int = map(1),
            uint = map(2),
            double = map(3),
            dateTime = map(4),
            bool = map(5),
            enum = map(6),
            list = map(7),
            set = map(8),
            map = map(9),
            valueObject = map(10),
            embeddedObject = map(11),
            multi = map(12),
            reference = map(13),
            listOfString = map(14)
        )

        override fun equals(other: Any?): Boolean {
            if (other !is DataModel<*, *>) return false

            @Suppress("UNCHECKED_CAST")
            val otherModel = other as DataModel<Any, PropertyDefinitions<Any>>

            if (this.name != otherModel.name) return false
            if (this.properties.size != otherModel.properties.size) return false

            return true
        }
    }
}

data class EmbeddedMarykObject(
    val value: String,
    val model: EmbeddedMarykObject? = null,
    val marykModel: TestMarykObject? = null
){
    object Properties : PropertyDefinitions<EmbeddedMarykObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(),
            getter = EmbeddedMarykObject::value
        )
        val model = add(
            index = 1, name = "model",
            definition = EmbeddedObjectDefinition(
                required = false,
                dataModel = { EmbeddedMarykObject }
            ),
            getter = EmbeddedMarykObject::model
        )
        val marykModel = add(
            index = 2, name = "marykModel",
            definition = EmbeddedObjectDefinition(
                required = false,
                dataModel = { TestMarykObject }
            ),
            getter = EmbeddedMarykObject::marykModel
        )
    }
    companion object: DataModel<EmbeddedMarykObject, Properties>(
        name = "EmbeddedMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = EmbeddedMarykObject(
            value = map(0),
            model = map(1),
            marykModel = map(2)
        )

        override fun equals(other: Any?): Boolean {
            if (other !is DataModel<*, *>) return false

            @Suppress("UNCHECKED_CAST")
            val otherModel = other as DataModel<Any, PropertyDefinitions<Any>>

            if (this.name != otherModel.name) return false
            if (this.properties.size != otherModel.properties.size) return false

            return true
        }
    }
}
