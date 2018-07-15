package maryk
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.definitions
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
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
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time

object TestMarykModel: RootDataModel<TestMarykModel, TestMarykModel.Properties>(
    name = "TestMarykModel",
    keyDefinitions = definitions(
        Properties.uint,
        Properties.bool,
        Properties.enum
    ),
    properties = Properties
) {
    object Properties: PropertyDefinitions() {
        val string = add(
            index = 0, name = "string",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        )

        val int = add(
            index = 1, name = "int",
            definition = NumberDefinition(
                type = SInt32,
                maxValue = 6
            )
        )

        val uint = add(
            index = 2, name = "uint",
            definition = NumberDefinition(
                type = UInt32,
                final = true
            )
        )

        val double = add(
            index = 3, name = "double",
            definition = NumberDefinition(type = Float64)
        )

        val dateTime = add(
            index = 4, name = "dateTime",
            definition = DateTimeDefinition()
        )

        val bool = add(
            index = 5, name = "bool",
            definition = BooleanDefinition(
                final = true
            )
        )

        val enum = add(
            index = 6, name = "enum",
            definition = EnumDefinition(
                enum = Option,
                default = Option.V0,
                final = true
            )
        )

        val list = add(
            index = 7, name = "list",
            definition = ListDefinition(
                required = false,
                valueDefinition = NumberDefinition(
                    type = SInt32
                )
            )
        )

        val set = add(
            index = 8, name = "set",
            definition = SetDefinition(
                required = false,
                valueDefinition = DateDefinition()
            )
        )

        val map = add(
            index = 9, name = "map",
            definition = MapDefinition(
                required = false,
                keyDefinition = TimeDefinition(),
                valueDefinition = StringDefinition()
            )
        )

        val valueObject = add(
            index = 10, name = "valueObject",
            definition = ValueModelDefinition(
                required = false,
                dataModel = TestValueObject
            )
        )

        val embeddedValues = add(
            index = 11, name = "embeddedValues",
            definition = EmbeddedValuesDefinition(
                required = false,
                dataModel = { EmbeddedMarykModel }
            )
        )

        val multi = add(
            index = 12, name = "multi",
            definition = MultiTypeDefinition<Option, IsPropertyContext>(
                required = false,
                typeEnum = Option,
                definitionMap = mapOf(
                    Option.V0 to StringDefinition(),
                    Option.V1 to NumberDefinition(type = SInt32),
                    Option.V2 to EmbeddedValuesDefinition(
                        dataModel = { EmbeddedMarykModel }
                    )
                )
            )
        )

        val reference = add(
            index = 13, name = "reference",
            definition = ReferenceDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
        )

        val listOfString = add(
            index = 14, name = "listOfString",
            definition = ListDefinition(
                required = false,
                valueDefinition = StringDefinition()
            )
        )

        @Suppress("unused")
        val selfReference = add(
            index = 15, name = "selfReference",
            definition = ReferenceDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
        )
    }

    operator fun invoke(
        string: String = "haha",
        int: Int,
        uint: UInt32,
        double: Double,
        dateTime: DateTime,
        bool: Boolean? = null,
        enum: Option = Option.V0,
        list: List<Int>? = null,
        set: Set<Date>? = null,
        map: Map<Time, String>? = null,
        valueObject: TestValueObject? = null,
        embeddedValues: Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>? = null,
        multi: TypedValue<Option, *>? = null,
        reference: Key<TestMarykObject.Companion>? = null,
        listOfString: List<String>? = null,
        selfReference: Key<TestMarykObject.Companion>? = null
    ) = this.map {
        mapNonNulls(
            this.string with string,
            this.int with int,
            this.uint with uint,
            this.double with double,
            this.dateTime with dateTime,
            this.bool with bool,
            this.enum with enum,
            this.list with list,
            this.set with set,
            this.map with map,
            this.valueObject with valueObject,
            this.embeddedValues with embeddedValues,
            this.multi with multi,
            this.reference with reference,
            this.listOfString with listOfString,
            this.selfReference with selfReference
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DataModel<*, *>) return false

        @Suppress("UNCHECKED_CAST")
        val otherModel = other as DataModel<*, PropertyDefinitions>

        if (this.name != otherModel.name) return false
        if (this.properties.size != otherModel.properties.size) return false

        return true
    }
}
