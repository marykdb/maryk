package maryk.generator.kotlin

import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedKotlinForSimpleDataModel = """
package maryk.test.models

import maryk.core.properties.RootDataModel
import maryk.core.properties.definitions.string

object SimpleMarykModel : RootDataModel<SimpleMarykModel>() {
    val value by string(
        index = 1u,
        default = "haha",
        regEx = "ha.*"
    )
}
""".trimIndent()

val generatedKotlinForCompleteDataModel = """
package maryk.test.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.RootDataModel
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.date
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.time
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values
import maryk.test.models.CompleteMarykModel.Properties.booleanForKey
import maryk.test.models.CompleteMarykModel.Properties.dateForKey
import maryk.test.models.CompleteMarykModel.Properties.dateTime
import maryk.test.models.CompleteMarykModel.Properties.multiForKey
import maryk.test.models.CompleteMarykModel.Properties.number
import maryk.test.models.CompleteMarykModel.Properties.subModel
import maryk.test.models.SimpleMarykModel.Properties.value

sealed class MarykEnumEmbedded(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<MarykEnumEmbedded>(index, alternativeNames) {
    object E1: MarykEnumEmbedded(1u)
    object E2: MarykEnumEmbedded(2u)
    object E3: MarykEnumEmbedded(3u)

    class UnknownMarykEnumEmbedded(index: UInt, override val name: String): MarykEnumEmbedded(index)

    companion object : IndexedEnumDefinition<MarykEnumEmbedded>(
        MarykEnumEmbedded::class,
        values = { arrayOf(E1, E2, E3) },
        unknownCreator = ::UnknownMarykEnumEmbedded
    )
}

object CompleteMarykModel : RootDataModel<CompleteMarykModel>(
    version = Version(2, 1)
    keyDefinition = Multiple(
        UUIDKey,
        multiForKey.typeRef(),
        booleanForKey.ref(),
        Reversed(dateForKey.ref())
    )
    indices = listOf(
        number.ref(),
        Reversed(dateTime.ref()),
        Multiple(
            booleanForKey.ref(),
            multiForKey.typeRef()
        ),
        value.ref(subModel.ref())
    ),
    reservedIndices = listOf(99u)
    reservedNames = listOf("reserved")
) {
    val string by string(
        index = 1u,
        alternativeNames = setOf("str", "stringValue"),
        required = false,
        unique = true,
        minValue = "a",
        maxValue = "zzzz",
        default = "string",
        minSize = 1u,
        maxSize = 10u,
        regEx = "ha.*"
    )
    val number by number(
        index = 2u,
        unique = true,
        type = UInt32,
        minValue = 12u,
        maxValue = 34u,
        default = 33u
    )
    val boolean by boolean(
        index = 3u,
        required = false,
        final = true,
        default = true
    )
    val enum by enum(
        index = 4u,
        required = false,
        final = true,
        unique = true,
        enum = Option,
        minValue = Option.V1,
        maxValue = Option.V3,
        default = Option.V1
    )
    val date by date(
        index = 5u,
        required = false,
        final = true,
        unique = true,
        minValue = LocalDate(1981, 12, 5),
        maxValue = LocalDate(2200, 12, 31),
        default = LocalDate(2018, 5, 2)
    )
    val dateTime by dateTime(
        index = 6u,
        required = false,
        final = true,
        unique = true,
        precision = TimePrecision.MILLIS,
        minValue = LocalDateTime(1981, 12, 5, 11),
        maxValue = LocalDateTime(2200, 12, 31, 23, 59, 59),
        default = LocalDateTime(2018, 5, 2, 10, 11, 12)
    )
    val time by time(
        index = 7u,
        required = false,
        final = true,
        unique = true,
        precision = TimePrecision.MILLIS,
        minValue = LocalTime(0, 0),
        maxValue = LocalTime(23, 59, 59, 999),
        default = LocalTime(10, 11, 12)
    )
    val fixedBytes by fixedBytes(
        index = 8u,
        required = false,
        final = true,
        unique = true,
        minValue = Bytes("AAAAAAA"),
        maxValue = Bytes("f39/f38"),
        default = Bytes("AAECAwQ"),
        byteSize = 5
    )
    val flexBytes by flexBytes(
        index = 9u,
        required = false,
        final = true,
        unique = true,
        minValue = Bytes("AA"),
        maxValue = Bytes("f39/f39/fw"),
        default = Bytes("AAECAw"),
        minSize = 1u,
        maxSize = 7u
    )
    val reference by reference(
        index = 10u,
        required = false,
        final = true,
        unique = true,
        minValue = Key("AA"),
        maxValue = Key("f39/f39/fw"),
        default = Key("AAECAQAAECAQAAECAQAAEA"),
        dataModel = { SimpleMarykModel }
    )
    val subModel by embed(
        index = 11u,
        required = false,
        final = true,
        dataModel = { SimpleMarykModel },
        default = SimpleMarykModel(
            value = "a default"
        )
    )
    val valueModel by valueObject(
        index = 12u,
        required = false,
        final = true,
        dataModel = ValueMarykObject,
        minValue = ValueMarykObject(
            int = 0,
            date = LocalDate(100, 1, 1)
        ),
        maxValue = ValueMarykObject(
            int = 999,
            date = LocalDate(9999, 12, 31)
        ),
        default = ValueMarykObject(
            int = 10,
            date = LocalDate(2010, 10, 10)
        )
    )
    val list by list(
        index = 13u,
        required = false,
        final = true,
        minSize = 1u,
        maxSize = 5u,
        valueDefinition = StringDefinition(
            regEx = "ha.*"
        ),
        default = listOf("ha1", "ha2", "ha3")
    )
    val set by set(
        index = 14u,
        required = false,
        final = true,
        minSize = 1u,
        maxSize = 5u,
        valueDefinition = NumberDefinition(
            type = SInt32
        ),
        default = setOf(1, 2, 3)
    )
    val map by map(
        index = 15u,
        required = false,
        final = true,
        minSize = 1u,
        maxSize = 5u,
        keyDefinition = DateDefinition(),
        valueDefinition = NumberDefinition(
            type = SInt32
        ),
        default = mapOf(LocalDate(2010, 11, 12) to 1, LocalDate(2011, 12, 13) to 1)
    )
    val multi by multiType(
        index = 16u,
        required = false,
        final = true,
        typeEnum = MarykTypeEnum,
        default = TypedValue(MarykTypeEnum.T1, "a value")
    )
    val booleanForKey by boolean(
        index = 17u,
        final = true
    )
    val dateForKey by date(
        index = 18u,
        final = true
    )
    val multiForKey by multiType(
        index = 19u,
        final = true,
        typeEnum = SimpleMarykTypeEnum
    )
    val enumEmbedded by enum(
        index = 20u,
        enum = MarykEnumEmbedded,
        minValue = MarykEnumEmbedded.E1
    )
    val mapWithEnum by map(
        index = 21u,
        required = false,
        final = true,
        minSize = 1u,
        maxSize = 5u,
        keyDefinition = EnumDefinition(
            enum = MarykEnumEmbedded
        ),
        valueDefinition = StringDefinition(),
        default = mapOf<MarykEnumEmbedded, String>(MarykEnumEmbedded.E1 to "value")
    )
    val mapWithList by map(
        index = 22u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = ListDefinition(
            valueDefinition = StringDefinition()
        ),
        default = mapOf("a" to listOf("b", "c"))
    )
    val mapWithSet by map(
        index = 23u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = SetDefinition(
            valueDefinition = StringDefinition()
        ),
        default = mapOf("a" to setOf("b", "c"))
    )
    val mapWithMap by map(
        index = 24u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = MapDefinition(
            keyDefinition = StringDefinition(),
            valueDefinition = StringDefinition()
        ),
        default = mapOf("a" to mapOf("b" to "c"))
    )
    val incMap by incrementingMap(
        index = 25u,
        required = false,
        keyNumberDescriptor = UInt32,
        valueDefinition = EmbeddedValuesDefinition(
            dataModel = { EmbeddedMarykModel }
        )
    )
    val location by geoPoint(
        index = 26u,
        required = false,
        final = true,
        default = GeoPoint(52.0906448, 5.1212607)
    )
}
""".trimIndent()

class GenerateKotlinForRootDataModelTest {
    @Test
    fun generateKotlinForSimpleModel() {
        val output = buildString {
            SimpleMarykModel.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(generatedKotlinForSimpleDataModel, output)
    }

    @Test
    fun generateKotlinForCompleteModel() {
        val generationContext = GenerationContext(
            enums = mutableListOf(MarykTypeEnum, Option, SimpleMarykTypeEnum)
        )
        val output = buildString {
            CompleteMarykModel.generateKotlin("maryk.test.models", generationContext) {
                append(it)
            }
        }

        assertEquals(generatedKotlinForCompleteDataModel, output)
    }
}
