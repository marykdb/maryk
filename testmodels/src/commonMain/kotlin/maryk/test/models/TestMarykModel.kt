package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.TestMarykModel.Properties.bool
import maryk.test.models.TestMarykModel.Properties.dateTime
import maryk.test.models.TestMarykModel.Properties.double
import maryk.test.models.TestMarykModel.Properties.enum
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.models.TestMarykModel.Properties.multi
import maryk.test.models.TestMarykModel.Properties.uint

object TestMarykModel : RootDataModel<TestMarykModel, TestMarykModel.Properties>(
    keyDefinition = Multiple(
        uint.ref(),
        bool.ref(),
        enum.ref()
    ),
    indices = listOf(
        Multiple(
            Reversed(dateTime.ref()),
            enum.ref(),
            int.ref()
        ),
        int.ref(),
        Reversed(double.ref()),
        multi.typeRef()
    ),
    reservedIndices = listOf(99u),
    reservedNames = listOf("reserved"),
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val string by define(
            index = 1u,
            alternativeNames = setOf("str", "stringValue")
        ) {
            StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        }

        val int by define(2u) {
            NumberDefinition(
                type = SInt32,
                maxValue = 6
            )
        }

        val uint by define(3u) {
            NumberDefinition(
                type = UInt32,
                final = true
            )
        }

        val double by define(4u) {
            NumberDefinition(type = Float64)
        }

        val dateTime by define(5u) {
            DateTimeDefinition()
        }

        val bool by define(6u) {
            BooleanDefinition(
                final = true
            )
        }

        val enum by define(7u) {
            EnumDefinition(
                enum = Option,
                default = Option.V1,
                final = true
            )
        }

        val list by define(8u) {
            ListDefinition(
                required = false,
                valueDefinition = NumberDefinition(
                    type = SInt32
                )
            )
        }

        val set by define(9u) {
            SetDefinition(
                required = false,
                maxSize = 5u,
                valueDefinition = DateDefinition(
                    maxValue = Date(2100, 12, 31)
                )
            )
        }

        val map by define(10u) {
            MapDefinition(
                required = false,
                maxSize = 5u,
                keyDefinition = TimeDefinition(
                    maxValue = Time(23, 0, 0)
                ),
                valueDefinition = StringDefinition(
                    maxSize = 10u
                )
            )
        }

        val valueObject by define(11u) {
            ValueModelDefinition(
                required = false,
                dataModel = TestValueObject
            )
        }

        val embeddedValues by define(12u) {
            EmbeddedValuesDefinition(
                required = false,
                dataModel = { EmbeddedMarykModel }
            )
        }

        val multi by define(13u) {
            MultiTypeDefinition(
                required = false,
                typeEnum = SimpleMarykTypeEnum
            )
        }

        val reference by define(14u) {
            ReferenceDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
        }

        val listOfString by define(15u) {
            ListDefinition(
                required = false,
                minSize = 1u,
                maxSize = 6u,
                valueDefinition = StringDefinition(
                    maxSize = 10u
                )
            )
        }

        val selfReference by define(16u) {
            ReferenceDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
        }

        val setOfString by define(17u) {
            SetDefinition(
                required = false,
                maxSize = 6u,
                valueDefinition = StringDefinition(
                    maxSize = 10u
                )
            )
        }

        val incMap by define(18u) {
            IncrementingMapDefinition(
                required = false,
                keyNumberDescriptor = UInt32,
                valueDefinition = StringDefinition()
            )
        }
    }

    operator fun invoke(
        string: String = "haha",
        int: Int,
        uint: UInt,
        double: Double,
        dateTime: DateTime,
        bool: Boolean? = null,
        enum: Option = Option.V1,
        list: List<Int>? = null,
        set: Set<Date>? = null,
        map: Map<Time, String>? = null,
        valueObject: TestValueObject? = null,
        embeddedValues: Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>? = null,
        multi: TypedValue<SimpleMarykTypeEnum<*>, *>? = null,
        reference: Key<TestMarykModel>? = null,
        listOfString: List<String>? = null,
        selfReference: Key<TestMarykModel>? = null,
        setOfString: Set<String>? = null,
        incMap: Map<UInt, String>? = null
    ) = this.values {
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
            this.selfReference with selfReference,
            this.setOfString with setOfString,
            this.incMap with incMap
        )
    }

    override fun equals(other: Any?) =
        other is DataModel<*, *> &&
            this.name == other.name &&
            this.properties.size == other.properties.size
}
