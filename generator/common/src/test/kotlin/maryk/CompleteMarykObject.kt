package maryk

import maryk.core.models.RootDataModel
import maryk.core.models.definitions
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.IsSubDefinition
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
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.TypeId
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time

enum class MarykEnumEmbedded(
    override val index: Int
): IndexedEnum<MarykEnumEmbedded> {
    E1(1),
    E2(2),
    E3(3);

    companion object: IndexedEnumDefinition<MarykEnumEmbedded>(
        "MarykEnumEmbedded", MarykEnumEmbedded::values
    )
}

data class CompleteMarykObject(
    val string: String = "string",
    val number: UInt32 = 42.toUInt32(),
    val boolean: Boolean = true,
    val enum: MarykEnum = MarykEnum.O1,
    val date: Date = Date(2018, 5, 2),
    val dateTime: DateTime = DateTime(2018, 5, 2, 10, 11, 12),
    val time: Time = Time(10, 11, 12),
    val fixedBytes: Bytes = Bytes("AAECAwQ"),
    val flexBytes: Bytes = Bytes("AAECAw"),
    val reference: Key<SimpleMarykObject> = Key("AAECAw"),
    val subModel: SimpleMarykObject = SimpleMarykObject(
        value = "a default"
    ),
    val valueModel: ValueMarykObject = ValueMarykObject(
        int = 10,
        date = Date(2010, 10, 10)
    ),
    val list: List<String> = listOf("ha1", "ha2", "ha3"),
    val set: Set<Int> = setOf(1, 2, 3),
    val map: Map<Date, Int> = mapOf(Date(2010, 11, 12) to 1, Date(2011, 12, 13) to 1),
    val multi: TypedValue<MarykEnum, *> = TypedValue(MarykEnum.O1, "a value"),
    val booleanForKey: Boolean,
    val dateForKey: Date,
    val multiForKey: TypedValue<MarykEnum, *>,
    val enumEmbedded: MarykEnumEmbedded
) {
    object Properties: PropertyDefinitions<CompleteMarykObject>() {
        val string = add(
            index = 0, name = "string",
            definition = StringDefinition(
                indexed = true,
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
                required = false,
                final = true,
                unique = true,
                enum = MarykEnum,
                minValue = MarykEnum.O1,
                maxValue = MarykEnum.O3,
                default = MarykEnum.O1
            ),
            getter = CompleteMarykObject::enum
        )
        val date = add(
            index = 4, name = "date",
            definition = DateDefinition(
                indexed = true,
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
            definition = EmbeddedObjectDefinition(
                indexed = true,
                required = false,
                final = true,
                dataModel = { SimpleMarykObject },
                default = SimpleMarykObject(
                    value = "a default"
                )
            ),
            getter = CompleteMarykObject::subModel
        )
        val valueModel = add(
            index = 11, name = "valueModel",
            definition = ValueModelDefinition(
                indexed = true,
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
            ),
            getter = CompleteMarykObject::valueModel
        )
        val list = add(
            index = 12, name = "list",
            definition = ListDefinition(
                indexed = true,
                required = false,
                final = true,
                minSize = 1,
                maxSize = 5,
                valueDefinition = StringDefinition(
                    regEx = "ha.*"
                ),
                default = listOf("ha1", "ha2", "ha3")
            ),
            getter = CompleteMarykObject::list
        )
        val set = add(
            index = 13, name = "set",
            definition = SetDefinition(
                indexed = true,
                required = false,
                final = true,
                minSize = 1,
                maxSize = 5,
                valueDefinition = NumberDefinition(
                    type = SInt32
                ),
                default = setOf(1, 2, 3)
            ),
            getter = CompleteMarykObject::set
        )
        val map = add(
            index = 14, name = "map",
            definition = MapDefinition(
                indexed = true,
                required = false,
                final = true,
                minSize = 1,
                maxSize = 5,
                keyDefinition = DateDefinition(),
                valueDefinition = NumberDefinition(
                    type = SInt32
                ),
                default = mapOf(Date(2010, 11, 12) to 1, Date(2011, 12, 13) to 1)
            ),
            getter = CompleteMarykObject::map
        )
        val multi = add(
            index = 15, name = "multi",
            definition = MultiTypeDefinition(
                indexed = true,
                required = false,
                final = true,
                typeEnum = MarykEnum,
                definitionMap = mapOf<MarykEnum, IsSubDefinition<*, IsPropertyContext>>(
                    MarykEnum.O1 to StringDefinition(
                        regEx = "hi.*"
                    ),
                    MarykEnum.O2 to BooleanDefinition()
                ),
                default = TypedValue(MarykEnum.O1, "a value")
            ),
            getter = CompleteMarykObject::multi
        )
        val booleanForKey = add(
            index = 16, name = "booleanForKey",
            definition = BooleanDefinition(
                final = true
            ),
            getter = CompleteMarykObject::booleanForKey
        )
        val dateForKey = add(
            index = 17, name = "dateForKey",
            definition = DateDefinition(
                final = true
            ),
            getter = CompleteMarykObject::dateForKey
        )
        val multiForKey = add(
            index = 18, name = "multiForKey",
            definition = MultiTypeDefinition(
                final = true,
                typeEnum = MarykEnum,
                definitionMap = mapOf<MarykEnum, IsSubDefinition<*, IsPropertyContext>>(
                    MarykEnum.O1 to StringDefinition(
                        regEx = "hi.*"
                    ),
                    MarykEnum.O2 to BooleanDefinition()
                )
            ),
            getter = CompleteMarykObject::multiForKey
        )
        val enumEmbedded = add(
            index = 19, name = "enumEmbedded",
            definition = EnumDefinition(
                enum = MarykEnumEmbedded,
                minValue = MarykEnumEmbedded.E1
            ),
            getter = CompleteMarykObject::enumEmbedded
        )
    }

    companion object: RootDataModel<CompleteMarykObject, Properties>(
        name = "CompleteMarykObject",
        keyDefinitions = definitions(
            UUIDKey,
            TypeId(Properties.multiForKey.getRef()),
            Properties.booleanForKey,
            Reversed(Properties.dateForKey.getRef())
        ),
        properties = Properties
    ) {
        override fun invoke(map: Values<CompleteMarykObject, Properties>) = CompleteMarykObject(
            string = map(0),
            number = map(1),
            boolean = map(2),
            enum = map(3),
            date = map(4),
            dateTime = map(5),
            time = map(6),
            fixedBytes = map(7),
            flexBytes = map(8),
            reference = map(9),
            subModel = map(10),
            valueModel = map(11),
            list = map(12),
            set = map(13),
            map = map(14),
            multi = map(15),
            booleanForKey = map(16),
            dateForKey = map(17),
            multiForKey = map(18),
            enumEmbedded = map(19)
        )
    }
}
