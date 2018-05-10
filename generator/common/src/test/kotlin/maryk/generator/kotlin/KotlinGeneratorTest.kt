package maryk.generator.kotlin

import maryk.CompleteMarykObject
import maryk.SimpleMarykObject
import maryk.test.shouldBe
import kotlin.test.Test

class KotlinGeneratorTest {
    @Test
    fun generate_kotlin_for_simple_model(){
        var output = ""

        SimpleMarykObject.generateKotlin("maryk.test") {
            output += it
        }

        output shouldBe """
        package maryk.test

        import maryk.core.objects.RootDataModel
        import maryk.core.properties.definitions.PropertyDefinitions
        import maryk.core.properties.definitions.StringDefinition

        data class SimpleMarykObject(
            val value: String = "haha"
        ) {
            object Properties: PropertyDefinitions<SimpleMarykObject>() {
                val value = add(
                    index = 0, name = "value",
                    definition = StringDefinition(
                        default = "haha",
                        regEx = "ha.*"
                    ),
                    getter = SimpleMarykObject::value
                )
            }

            companion object: RootDataModel<SimpleMarykObject, Properties>(
                name = "SimpleMarykObject",
                properties = Properties
            ) {
                override fun invoke(map: Map<Int, *>) = SimpleMarykObject(
                    value = map(0, "haha")
                )
            }
        }
        """.trimIndent()
    }

    @Test
    fun generate_kotlin_for_complete_model(){
        var output = ""

        CompleteMarykObject.generateKotlin("maryk.test") {
            output += it
        }

        output shouldBe """
        package maryk.test

        import maryk.core.objects.RootDataModel
        import maryk.core.properties.definitions.BooleanDefinition
        import maryk.core.properties.definitions.DateDefinition
        import maryk.core.properties.definitions.DateTimeDefinition
        import maryk.core.properties.definitions.EnumDefinition
        import maryk.core.properties.definitions.FixedBytesDefinition
        import maryk.core.properties.definitions.FlexBytesDefinition
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
        import maryk.core.properties.types.Bytes
        import maryk.core.properties.types.Key
        import maryk.core.properties.types.TimePrecision
        import maryk.core.properties.types.numeric.UInt32
        import maryk.core.properties.types.numeric.toUInt32
        import maryk.lib.time.Date
        import maryk.lib.time.DateTime
        import maryk.lib.time.Time

        data class CompleteMarykObject(
            val string: String = "string",
            val number: UInt32 = 42.toUInt32(),
            val boolean: Boolean = true,
            val enum: Option = Option.O1,
            val date: Date = Date(2018, 5, 2),
            val dateTime: DateTime = DateTime(2018, 5, 2, 10, 11, 12),
            val time: Time = Time(10, 11, 12),
            val fixedBytes: Bytes = Bytes("AAECAwQ"),
            val flexBytes: Bytes = Bytes("AAECAw"),
            val reference: Key<SimpleMarykObject> = Key("AAECAw"),
            val subModel: SimpleMarykObject,
            val valueModel: ValueMarykObject,
            val list: List<String>,
            val set: Set<Int>,
            val map: Map<Date, Int>,
            val multi: TypedValue<Option, *>
        ) {
            object Properties: PropertyDefinitions<CompleteMarykObject>() {
                val string = add(
                    index = 0, name = "string",
                    definition = StringDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        minValue = "a",
                        maxValue = "zzzz",
                        default = "string",
                        minSize = 1,
                        maxSize = 10,
                        regEx = "ha.*"
                    ),
                    getter = CompleteMarykObject::string
                )
                val number = add(
                    index = 1, name = "number",
                    definition = NumberDefinition(
                        indexed = true,
                        searchable = false,
                        final = true,
                        unique = true,
                        type = UInt32,
                        minValue = 12.toUInt32(),
                        maxValue = 34.toUInt32(),
                        default = 42.toUInt32(),
                        random = true
                    ),
                    getter = CompleteMarykObject::number
                )
                val boolean = add(
                    index = 2, name = "boolean",
                    definition = BooleanDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        default = true
                    ),
                    getter = CompleteMarykObject::boolean
                )
                val enum = add(
                    index = 3, name = "enum",
                    definition = EnumDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        minValue = Option.O1,
                        maxValue = Option.O3,
                        default = Option.O1,
                        name = Option,
                        values = Option.values()
                    ),
                    getter = CompleteMarykObject::enum
                )
                val date = add(
                    index = 4, name = "date",
                    definition = DateDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        minValue = Date(1981, 12, 5),
                        maxValue = Date(2200, 12, 31),
                        default = Date(2018, 5, 2),
                        fillWithNow = true
                    ),
                    getter = CompleteMarykObject::date
                )
                val dateTime = add(
                    index = 5, name = "dateTime",
                    definition = DateTimeDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        precision = TimePrecision.MILLIS,
                        minValue = DateTime(1981, 12, 5, 11),
                        maxValue = DateTime(2200, 12, 31, 23, 59, 59),
                        default = DateTime(2018, 5, 2, 10, 11, 12),
                        fillWithNow = true
                    ),
                    getter = CompleteMarykObject::dateTime
                )
                val time = add(
                    index = 6, name = "time",
                    definition = TimeDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        precision = TimePrecision.MILLIS,
                        minValue = Time(0, 0),
                        maxValue = Time(23, 59, 59, 999),
                        default = Time(10, 11, 12),
                        fillWithNow = true
                    ),
                    getter = CompleteMarykObject::time
                )
                val fixedBytes = add(
                    index = 7, name = "fixedBytes",
                    definition = FixedBytesDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        minValue = Bytes("AAAAAAA"),
                        maxValue = Bytes("f39/f38"),
                        default = Bytes("AAECAwQ"),
                        random = true,
                        byteSize = 5
                    ),
                    getter = CompleteMarykObject::fixedBytes
                )
                val flexBytes = add(
                    index = 8, name = "flexBytes",
                    definition = FlexBytesDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        minValue = Bytes("AA"),
                        maxValue = Bytes("f39/f39/fw"),
                        default = Bytes("AAECAw"),
                        minSize = 1,
                        maxSize = 7
                    ),
                    getter = CompleteMarykObject::flexBytes
                )
                val reference = add(
                    index = 9, name = "reference",
                    definition = ReferenceDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        unique = true,
                        minValue = Key("AA"),
                        maxValue = Key("f39/f39/fw"),
                        default = Key("AAECAw"),
                        dataModel = { SimpleMarykObject }
                    ),
                    getter = CompleteMarykObject::reference
                )
                val subModel = add(
                    index = 10, name = "subModel",
                    definition = SubModelDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        dataModel = { SimpleMarykObject }
                    ),
                    getter = CompleteMarykObject::subModel
                )
                val valueModel = add(
                    index = 11, name = "valueModel",
                    definition = ValueModelDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        dataModel = { ValueMarykObject }
                    ),
                    getter = CompleteMarykObject::valueModel
                )
                val list = add(
                    index = 12, name = "list",
                    definition = ListDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        minSize = 1,
                        maxSize = 5,
                        valueDefinition = StringDefinition(
                            regEx = "ha.*"
                        )
                    ),
                    getter = CompleteMarykObject::list
                )
                val set = add(
                    index = 13, name = "set",
                    definition = SetDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        minSize = 1,
                        maxSize = 5,
                        valueDefinition = NumberDefinition(
                            type = SInt32
                        )
                    ),
                    getter = CompleteMarykObject::set
                )
                val map = add(
                    index = 14, name = "map",
                    definition = MapDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        minSize = 1,
                        maxSize = 5,
                        keyDefinition = DateDefinition(),
                        valueDefinition = NumberDefinition(
                            type = SInt32
                        )
                    ),
                    getter = CompleteMarykObject::map
                )
                val multi = add(
                    index = 15, name = "multi",
                    definition = MultiTypeDefinition(
                        indexed = true,
                        searchable = false,
                        required = false,
                        final = true,
                        typeEnum = Option,
                        definitionMap = mapOf<Option, IsSubDefinition<*, IsPropertyContext>>(
                            Option.O1 to StringDefinition(
                                regEx = "hi.*"
                            ),
                            Option.O2 to BooleanDefinition()
                        )
                    ),
                    getter = CompleteMarykObject::multi
                )
            }

            companion object: RootDataModel<CompleteMarykObject, Properties>(
                name = "CompleteMarykObject",
                properties = Properties
            ) {
                override fun invoke(map: Map<Int, *>) = CompleteMarykObject(
                    string = map(0, "string"),
                    number = map(1, 42.toUInt32()),
                    boolean = map(2, true),
                    enum = map(3, Option.O1),
                    date = map(4, Date(2018, 5, 2)),
                    dateTime = map(5, DateTime(2018, 5, 2, 10, 11, 12)),
                    time = map(6, Time(10, 11, 12)),
                    fixedBytes = map(7, Bytes("AAECAwQ")),
                    flexBytes = map(8, Bytes("AAECAw")),
                    reference = map(9, Key("AAECAw")),
                    subModel = map(10),
                    valueModel = map(11),
                    list = map(12),
                    set = map(13),
                    map = map(14),
                    multi = map(15)
                )
            }
        }
        """.trimIndent()
    }
}
