package maryk.generator.kotlin

import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnum
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForSimpleDataModel = """
package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

object SimpleMarykModel : RootDataModel<SimpleMarykModel, SimpleMarykModel.Properties>(
    name = "SimpleMarykModel",
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val value = add(
            index = 1, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        )
    }

    operator fun invoke(
        value: String = "haha"
    ) = values {
        mapNonNulls(
            this.value with value
        )
    }
}
""".trimIndent()

val generatedKotlinForCompleteDataModel = """
package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.definitions.key.Multiple
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.CompleteMarykModel.Properties.booleanForKey
import maryk.test.models.CompleteMarykModel.Properties.dateForKey
import maryk.test.models.CompleteMarykModel.Properties.dateTime
import maryk.test.models.CompleteMarykModel.Properties.multiForKey
import maryk.test.models.CompleteMarykModel.Properties.number
import maryk.test.models.CompleteMarykModel.Properties.subModel
import maryk.test.models.SimpleMarykModel.Properties.value

enum class MarykEnumEmbedded(
    override val index: UInt
) : IndexedEnum<MarykEnumEmbedded> {
    E1(1u),
    E2(2u),
    E3(3u);

    companion object : IndexedEnumDefinition<MarykEnumEmbedded>(
        "MarykEnumEmbedded", MarykEnumEmbedded::values
    )
}

object CompleteMarykModel : RootDataModel<CompleteMarykModel, CompleteMarykModel.Properties>(
    name = "CompleteMarykModel",
    keyDefinitions = Multiple(
        UUIDKey,
        multiForKey.typeRef(),
        booleanForKey.ref(),
        Reversed(dateForKey.ref())
    ),
    indices = listOf(
        number.ref(),
        Reversed(dateTime.ref()),
        Multiple(
            booleanForKey.ref(),
            multiForKey.typeRef()
        ),
        value.ref(subModel.ref())
    ),
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val string = add(
            index = 1, name = "string",
            definition = StringDefinition(
                required = false,
                final = true,
                unique = true,
                minValue = "a",
                maxValue = "zzzz",
                default = "string",
                minSize = 1u,
                maxSize = 10u,
                regEx = "ha.*"
            )
        )
        val number = add(
            index = 2, name = "number",
            definition = NumberDefinition(
                final = true,
                unique = true,
                type = UInt32,
                minValue = 12u,
                maxValue = 34u,
                default = 42u,
                random = true
            )
        )
        val boolean = add(
            index = 3, name = "boolean",
            definition = BooleanDefinition(
                required = false,
                final = true,
                default = true
            )
        )
        val enum = add(
            index = 4, name = "enum",
            definition = EnumDefinition(
                required = false,
                final = true,
                unique = true,
                enum = MarykEnum,
                minValue = MarykEnum.O1,
                maxValue = MarykEnum.O3,
                default = MarykEnum.O1
            )
        )
        val date = add(
            index = 5, name = "date",
            definition = DateDefinition(
                required = false,
                final = true,
                unique = true,
                minValue = Date(1981, 12, 5),
                maxValue = Date(2200, 12, 31),
                default = Date(2018, 5, 2),
                fillWithNow = true
            )
        )
        val dateTime = add(
            index = 6, name = "dateTime",
            definition = DateTimeDefinition(
                required = false,
                final = true,
                unique = true,
                precision = TimePrecision.MILLIS,
                minValue = DateTime(1981, 12, 5, 11),
                maxValue = DateTime(2200, 12, 31, 23, 59, 59),
                default = DateTime(2018, 5, 2, 10, 11, 12),
                fillWithNow = true
            )
        )
        val time = add(
            index = 7, name = "time",
            definition = TimeDefinition(
                required = false,
                final = true,
                unique = true,
                precision = TimePrecision.MILLIS,
                minValue = Time(0, 0),
                maxValue = Time(23, 59, 59, 999),
                default = Time(10, 11, 12),
                fillWithNow = true
            )
        )
        val fixedBytes = add(
            index = 8, name = "fixedBytes",
            definition = FixedBytesDefinition(
                required = false,
                final = true,
                unique = true,
                minValue = Bytes("AAAAAAA"),
                maxValue = Bytes("f39/f38"),
                default = Bytes("AAECAwQ"),
                random = true,
                byteSize = 5
            )
        )
        val flexBytes = add(
            index = 9, name = "flexBytes",
            definition = FlexBytesDefinition(
                required = false,
                final = true,
                unique = true,
                minValue = Bytes("AA"),
                maxValue = Bytes("f39/f39/fw"),
                default = Bytes("AAECAw"),
                minSize = 1u,
                maxSize = 7u
            )
        )
        val reference = add(
            index = 10, name = "reference",
            definition = ReferenceDefinition(
                required = false,
                final = true,
                unique = true,
                minValue = Key("AA"),
                maxValue = Key("f39/f39/fw"),
                default = Key("AAECAQAAECAQAAECAQAAEA"),
                dataModel = { SimpleMarykModel }
            )
        )
        val subModel = add(
            index = 11, name = "subModel",
            definition = EmbeddedValuesDefinition(
                required = false,
                final = true,
                dataModel = { SimpleMarykModel },
                default = SimpleMarykModel(
                    value = "a default"
                )
            )
        )
        val valueModel = add(
            index = 12, name = "valueModel",
            definition = ValueModelDefinition(
                required = false,
                final = true,
                dataModel = ValueMarykObject,
                minValue = ValueMarykObject(
                    int = 0,
                    date = Date(100, 1, 1)
                ),
                maxValue = ValueMarykObject(
                    int = 999,
                    date = Date(9999, 12, 31)
                ),
                default = ValueMarykObject(
                    int = 10,
                    date = Date(2010, 10, 10)
                )
            )
        )
        val list = add(
            index = 13, name = "list",
            definition = ListDefinition(
                required = false,
                final = true,
                minSize = 1u,
                maxSize = 5u,
                valueDefinition = StringDefinition(
                    regEx = "ha.*"
                ),
                default = listOf("ha1", "ha2", "ha3")
            )
        )
        val set = add(
            index = 14, name = "set",
            definition = SetDefinition(
                required = false,
                final = true,
                minSize = 1u,
                maxSize = 5u,
                valueDefinition = NumberDefinition(
                    type = SInt32
                ),
                default = setOf(1, 2, 3)
            )
        )
        val map = add(
            index = 15, name = "map",
            definition = MapDefinition(
                required = false,
                final = true,
                minSize = 1u,
                maxSize = 5u,
                keyDefinition = DateDefinition(),
                valueDefinition = NumberDefinition(
                    type = SInt32
                ),
                default = mapOf(Date(2010, 11, 12) to 1, Date(2011, 12, 13) to 1)
            )
        )
        val multi = add(
            index = 16, name = "multi",
            definition = MultiTypeDefinition(
                required = false,
                final = true,
                typeEnum = MarykEnum,
                definitionMap = definitionMap(
                    MarykEnum.O1 to StringDefinition(
                        regEx = "hi.*"
                    ),
                    MarykEnum.O2 to BooleanDefinition()
                ),
                default = TypedValue(MarykEnum.O1, "a value")
            )
        )
        val booleanForKey = add(
            index = 17, name = "booleanForKey",
            definition = BooleanDefinition(
                final = true
            )
        )
        val dateForKey = add(
            index = 18, name = "dateForKey",
            definition = DateDefinition(
                final = true
            )
        )
        val multiForKey = add(
            index = 19, name = "multiForKey",
            definition = MultiTypeDefinition(
                final = true,
                typeEnum = MarykEnum,
                definitionMap = definitionMap(
                    MarykEnum.O1 to StringDefinition(
                        regEx = "hi.*"
                    ),
                    MarykEnum.O2 to BooleanDefinition()
                )
            )
        )
        val enumEmbedded = add(
            index = 20, name = "enumEmbedded",
            definition = EnumDefinition(
                enum = MarykEnumEmbedded,
                minValue = MarykEnumEmbedded.E1
            )
        )
        val mapWithEnum = add(
            index = 21, name = "mapWithEnum",
            definition = MapDefinition(
                required = false,
                final = true,
                minSize = 1u,
                maxSize = 5u,
                keyDefinition = EnumDefinition(
                    enum = MarykEnumEmbedded
                ),
                valueDefinition = StringDefinition(),
                default = mapOf(MarykEnumEmbedded.E1 to "value")
            )
        )
    }

    operator fun invoke(
        string: String = "string",
        number: UInt = 42u,
        boolean: Boolean = true,
        enum: MarykEnum = MarykEnum.O1,
        date: Date = Date(2018, 5, 2),
        dateTime: DateTime = DateTime(2018, 5, 2, 10, 11, 12),
        time: Time = Time(10, 11, 12),
        fixedBytes: Bytes = Bytes("AAECAwQ"),
        flexBytes: Bytes = Bytes("AAECAw"),
        reference: Key<SimpleMarykModel> = Key("AAECAQAAECAQAAECAQAAEA"),
        subModel: Values<SimpleMarykModel, SimpleMarykModel.Properties> = SimpleMarykModel(
            value = "a default"
        ),
        valueModel: ValueMarykObject = ValueMarykObject(
            int = 10,
            date = Date(2010, 10, 10)
        ),
        list: List<String> = listOf("ha1", "ha2", "ha3"),
        set: Set<Int> = setOf(1, 2, 3),
        map: Map<Date, Int> = mapOf(Date(2010, 11, 12) to 1, Date(2011, 12, 13) to 1),
        multi: TypedValue<MarykEnum, *> = TypedValue(MarykEnum.O1, "a value"),
        booleanForKey: Boolean,
        dateForKey: Date,
        multiForKey: TypedValue<MarykEnum, *>,
        enumEmbedded: MarykEnumEmbedded,
        mapWithEnum: Map<MarykEnumEmbedded, String> = mapOf(MarykEnumEmbedded.E1 to "value")
    ) = values {
        mapNonNulls(
            this.string with string,
            this.number with number,
            this.boolean with boolean,
            this.enum with enum,
            this.date with date,
            this.dateTime with dateTime,
            this.time with time,
            this.fixedBytes with fixedBytes,
            this.flexBytes with flexBytes,
            this.reference with reference,
            this.subModel with subModel,
            this.valueModel with valueModel,
            this.list with list,
            this.set with set,
            this.map with map,
            this.multi with multi,
            this.booleanForKey with booleanForKey,
            this.dateForKey with dateForKey,
            this.multiForKey with multiForKey,
            this.enumEmbedded with enumEmbedded,
            this.mapWithEnum with mapWithEnum
        )
    }
}
""".trimIndent()

class GenerateKotlinForRootDataModelTest {
    @Test
    fun generateKotlinForSimpleModel() {
        var output = ""

        SimpleMarykModel.generateKotlin("maryk.test.models") {
            output += it
        }

        output shouldBe generatedKotlinForSimpleDataModel
    }

    @Test
    fun generateKotlinForCompleteModel() {
        var output = ""

        val generationContext = GenerationContext(
            enums = mutableListOf(MarykEnum)
        )

        CompleteMarykModel.generateKotlin("maryk.test.models", generationContext) {
            output += it
        }

        output shouldBe generatedKotlinForCompleteDataModel
    }
}
