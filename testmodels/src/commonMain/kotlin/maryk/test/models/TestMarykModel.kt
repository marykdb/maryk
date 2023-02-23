package maryk.test.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.RootDataModel
import maryk.core.properties.RootModel
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values

object TestMarykModel : RootModel<TestMarykModel>(
    keyDefinition = {
        TestMarykModel.run {
            Multiple(
                uint.ref(),
                bool.ref(),
                enum.ref()
            )
        }
    },
    indices = {
        TestMarykModel.run {
            listOf(
                Multiple(
                    Reversed(dateTime.ref()),
                    enum.ref(),
                    int.ref()
                ),
                int.ref(),
                Reversed(double.ref()),
                multi.typeRef(),
                uint.ref()
            )
        }
    },
    reservedIndices = listOf(99u),
    reservedNames = listOf("reserved"),
) {
    val string by string(
        index = 1u,
        alternativeNames = setOf("str", "stringValue"),
        default = "haha",
        regEx = "ha.*"
    )

    val int by number(
        index = 2u,
        type = SInt32,
        maxValue = 6
    )

    val uint by number(
        index = 3u,
        type = UInt32,
        final = true
    )

    val double by number(
        index = 4u,
        type = Float64
    )

    val dateTime by dateTime(
        index = 5u
    )

    val bool by boolean(
        index = 6u,
        final = true
    )

    val enum by enum(
        index = 7u,
        enum = Option,
        default = Option.V1,
        final = true
    )

    val list by list(
        index = 8u,
        required = false,
        valueDefinition = NumberDefinition(
            type = SInt32
        )
    )

    val set by set(
        index = 9u,
        required = false,
        maxSize = 5u,
        valueDefinition = DateDefinition(
            maxValue = LocalDate(2100, 12, 31)
        )
    )

    val map by map(
        index = 10u,
        required = false,
        maxSize = 5u,
        keyDefinition = TimeDefinition(
            maxValue = LocalTime(23, 0, 0)
        ),
        valueDefinition = StringDefinition(
            maxSize = 10u
        )
    )

    val valueObject by valueObject(
        index = 11u,
        required = false,
        dataModel = TestValueObject
    )

    val embeddedValues by embed(
        index = 12u,
        required = false,
        dataModel = { EmbeddedMarykModel.Model }
    )

    val multi by multiType(
        index = 13u,
        required = false,
        typeEnum = SimpleMarykTypeEnum
    )

    val reference by reference(
        14u,
        required = false,
        dataModel = { TestMarykModel.Model }
    )

    val listOfString by list(
        index = 15u,
        required = false,
        minSize = 1u,
        maxSize = 6u,
        valueDefinition = StringDefinition(
            maxSize = 10u
        )
    )

    val selfReference by reference(
        16u,
        required = false,
        dataModel = { TestMarykModel.Model }
    )

    val setOfString by set(
        index = 17u,
        valueDefinition = StringDefinition(
            maxSize = 10u
        ),
        maxSize = 6u,
        required = false
    )

    val incMap by incrementingMap(
        index = 18u,
        keyNumberDescriptor = UInt32,
        valueDefinition = StringDefinition(),
        required = false
    )

    operator fun invoke(
        string: String = "haha",
        int: Int,
        uint: UInt,
        double: Double,
        dateTime: LocalDateTime,
        bool: Boolean? = null,
        enum: Option = Option.V1,
        list: List<Int>? = null,
        set: Set<LocalDate>? = null,
        map: Map<LocalTime, String>? = null,
        valueObject: TestValueObject? = null,
        embeddedValues: Values<EmbeddedMarykModel>? = null,
        multi: TypedValue<SimpleMarykTypeEnum<*>, *>? = null,
        reference: Key<RootDataModel<TestMarykModel>>? = null,
        listOfString: List<String>? = null,
        selfReference: Key<RootDataModel<TestMarykModel>>? = null,
        setOfString: Set<String>? = null,
        incMap: Map<UInt, String>? = null
    ) = create(
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
