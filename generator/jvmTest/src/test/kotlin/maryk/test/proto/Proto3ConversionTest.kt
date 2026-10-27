package maryk.test.proto

import com.google.protobuf.ByteString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import maryk.MarykTestProtos
import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.time
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykEnumEmbedded.E2
import maryk.test.models.NumericMarykModel
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import maryk.test.models.ValueMarykObject
import maryk.generator.proto3.generateProto3
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.expect
import kotlin.time.ExperimentalTime

private object NanosTimeModel : RootDataModel<NanosTimeModel>() {
    val time by time(index = 1u, precision = NANOS)
}

private object NanosDateTimeModel : RootDataModel<NanosDateTimeModel>() {
    val dateTime by dateTime(index = 1u, precision = NANOS)
}

private object NanosDateTimeKeyModel : RootDataModel<NanosDateTimeKeyModel>() {
    val events by map(1u, keyDefinition = DateTimeDefinition(precision = NANOS), valueDefinition = StringDefinition())
}

class Proto3ConversionTest {
    @Test
    fun nanosecondDateTimeMapKeysRoundTripThroughStandardProto() {
        for (dateTime in listOf(LocalDateTime(1969, 12, 31, 23, 59, 59, 999_999_999), LocalDateTime(2026, 10, 27, 12, 0, 0, 123_456_789))) {
            val values = NanosDateTimeKeyModel.create { events with mapOf(dateTime to "event") }
            val bytes = ByteCollector()
            val cache = WriteCache()
            bytes.reserve(NanosDateTimeKeyModel.Serializer.calculateProtoBufLength(values, cache))
            NanosDateTimeKeyModel.Serializer.writeProtoBuf(values, cache, bytes::write)
            val instant = dateTime.toInstant(TimeZone.UTC)
            val standard = MarykTestProtos.NanosDateTimeKeyModel.parseFrom(bytes.bytes!!).eventsList.single()
            assertEquals(instant.epochSeconds, standard.key.seconds)
            assertEquals(instant.nanosecondsOfSecond, standard.key.nanos)
            assertEquals("event", standard.value)
            val standardBytes = MarykTestProtos.NanosDateTimeKeyModel.newBuilder().addEvents(
                MarykTestProtos.NanosDateTimeKeyModel.EventsEntry.newBuilder().setKey(
                    MarykTestProtos.NanosDateTimeKeyModel.EventsNanosDateTime.newBuilder()
                        .setSeconds(instant.epochSeconds).setNanos(instant.nanosecondsOfSecond)
                ).setValue("event")
            ).build().toByteArray()
            var offset = 0
            val decoded = NanosDateTimeKeyModel.Serializer.readProtoBuf(standardBytes.size, { standardBytes[offset++] })
            assertEquals(mapOf(dateTime to "event"), decoded { events })
        }
    }

    @Test
    fun compiledFixtureContainsCurrentGeneratedProtoGraph() {
        val generated = mutableMapOf<String, StringBuilder>()
        Definitions(
            Option,
            NumericMarykModel,
            SimpleMarykModel,
            ValueMarykObject,
            TestMarykModel,
            EmbeddedMarykModel,
            CompleteMarykModel,
            NanosTimeModel,
            NanosDateTimeModel,
            NanosDateTimeKeyModel,
        ).generateProto3 { name ->
            generated.getOrPut(name, ::StringBuilder)::append
        }

        val compiledFixture = checkNotNull(javaClass.getResource("/MarykTestProtos.proto"))
            .readText()
            .canonicalProto()
        generated.forEach { (name, schema) ->
            assertTrue(
                compiledFixture.contains(schema.toString().canonicalProto()),
                "Compiled fixture is stale for generated $name schema",
            )
        }
    }

    @Test
    fun nanosecondTimesRoundTripThroughStandardProto() {
        for (nanos in listOf(4_294_967_296L, 86_399_999_999_999L)) {
            val time = LocalTime.fromNanosecondOfDay(nanos)
            val values = NanosTimeModel.create { this.time with time }
            val bytes = ByteCollector()
            val cache = WriteCache()
            bytes.reserve(NanosTimeModel.Serializer.calculateProtoBufLength(values, cache))
            NanosTimeModel.Serializer.writeProtoBuf(values, cache, bytes::write)
            assertEquals(nanos, MarykTestProtos.NanosTimeModel.parseFrom(bytes.bytes!!).time)

            val standardBytes = MarykTestProtos.NanosTimeModel.newBuilder().setTime(nanos).build().toByteArray()
            var index = 0
            val decoded = NanosTimeModel.Serializer.readProtoBuf(standardBytes.size, { standardBytes[index++] })
            assertEquals(time, decoded { this.time })
        }
    }

    @Test
    fun nanosecondDateTimesRoundTripThroughStandardProto() {
        for (dateTime in listOf(
            LocalDateTime(1969, 12, 31, 23, 59, 59, 999_999_999),
            LocalDateTime(2026, 10, 27, 12, 0, 0, 123_456_789),
        )) {
            val values = NanosDateTimeModel.create { this.dateTime with dateTime }
            val bytes = ByteCollector()
            val cache = WriteCache()
            bytes.reserve(NanosDateTimeModel.Serializer.calculateProtoBufLength(values, cache))
            NanosDateTimeModel.Serializer.writeProtoBuf(values, cache, bytes::write)
            val instant = dateTime.toInstant(TimeZone.UTC)
            val standard = MarykTestProtos.NanosDateTimeModel.parseFrom(bytes.bytes!!).dateTime
            assertEquals(instant.epochSeconds, standard.seconds)
            assertEquals(instant.nanosecondsOfSecond, standard.nanos)
            val standardBytes = MarykTestProtos.NanosDateTimeModel.newBuilder().setDateTime(
                MarykTestProtos.NanosDateTimeModel.DateTimeNanosDateTime.newBuilder()
                    .setSeconds(instant.epochSeconds).setNanos(instant.nanosecondsOfSecond)
            ).build().toByteArray()
            var index = 0
            val decoded = NanosDateTimeModel.Serializer.readProtoBuf(standardBytes.size, { standardBytes[index++] })
            assertEquals(dateTime, decoded { this.dateTime })
        }
    }

    @Test
    fun testSimpleMarykModel(){
        // SimpleObject to convert
        val simpleObject = SimpleMarykModel.create { value with "testSimpleMarykModel" }
        val simpleObjectProto = MarykTestProtos.SimpleMarykModel.newBuilder().setValue("testSimpleMarykModel").build()

        // Write protobuf
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            SimpleMarykModel.Serializer.calculateProtoBufLength(simpleObject, cache)
        )
        SimpleMarykModel.Serializer.writeProtoBuf(simpleObject, cache, bc::write)

        val protoBufByteArray = simpleObjectProto.toByteArray()

        // Compare result
        expect(bc.bytes!!.toHexString()) { protoBufByteArray.toHexString() }
    }

    @Test
    fun testNumericMarykModel(){
        // SimpleObject to convert
        val numericObject = NumericMarykModel.create {}
        val numericObjectProto = MarykTestProtos.NumericMarykModel
            .newBuilder()
            .setSInt8(4)
            .setSInt16(42)
            .setSInt32(42)
            .setSInt64(4123123344572L)
            .setUInt8(4)
            .setUInt16(42)
            .setUInt32(42)
            .setUInt64(4123123344572L)
            .setFloat32(42.345F)
            .setFloat64(2345762.3123)
            .build()

        expect(java.lang.Float.floatToRawIntBits(42.345F)) {
            42.345F.toRawBits()
        }

        // Write protobuf
        val bc = ByteCollector()
        val cache = WriteCache()
        bc.reserve(
            NumericMarykModel.Serializer.calculateProtoBufLength(numericObject, cache)
        )
        NumericMarykModel.Serializer.writeProtoBuf(numericObject, cache, bc::write)

        val protoBufByteArray = numericObjectProto.toByteArray()

        // Compare result
        expect(bc.bytes!!.toHexString()) { protoBufByteArray.toHexString() }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testCompleteMarykObject(){
        // SimpleObject to convert
        val completeObject = CompleteMarykModel.create {
            booleanForKey with true
            dateForKey with LocalDate(2018, 7, 25)
            multiForKey with S1("string")
            enumEmbedded with E1
            mapWithEnum with mapOf(
                E2 to "mapped"
            )
        }

        val completeObjectProto = MarykTestProtos.CompleteMarykModel.newBuilder()
            .setString(completeObject { string })
            .setNumber(completeObject { number }!!.toLong())
            .setBoolean(completeObject { boolean }!!)
            .setEnum(MarykTestProtos.Option.V1)
            .setDate(completeObject { date }!!.toEpochDays().toInt())
            .setDateTime(completeObject { dateTime }!!.toInstant(TimeZone.UTC).toEpochMilliseconds())
            .setTime(completeObject { time }!!.toMillisecondOfDay())
            .setFixedBytes(ByteString.copyFrom(Bytes("AAECAwQ").bytes))
            .setFlexBytes(ByteString.copyFrom(Bytes("AAECAw").bytes))
            .setReference(ByteString.copyFrom(Key<SimpleMarykModel>("AAECAQAAECAQAAECAQAAEA").bytes))
            .setSubModel(MarykTestProtos.SimpleMarykModel.newBuilder().setValue("haha"))
            .setValueModel(ByteString.copyFrom(completeObject { valueModel }!!.toByteArray()))
            .addAllList(mutableListOf("ha1", "ha2", "ha3"))
            .addAllSet(mutableListOf(1, 2, 3))
            .putMap(LocalDate(2010, 11, 12).toEpochDays().toInt(), 1)
            .putMap(LocalDate(2011, 12, 13).toEpochDays().toInt(), 1)/**/
            .setMulti(MarykTestProtos.CompleteMarykModel.MultiType.newBuilder().setT1("a value"))
            .setBooleanForKey(completeObject { booleanForKey }!!)
            .setDateForKey(completeObject { dateForKey }!!.toEpochDays().toInt())
            .setMultiForKey(
                MarykTestProtos.CompleteMarykModel.MultiForKeyType.newBuilder().setS1("string")
            )
            .setEnumEmbedded(
                MarykTestProtos.CompleteMarykModel.MarykEnumEmbedded.E1
            )
            .addMapWithEnum(
                MarykTestProtos.CompleteMarykModel.MapWithEnumEntry.newBuilder().setKey(
                    MarykTestProtos.CompleteMarykModel.MarykEnumEmbedded.E2
                ).setValue("mapped")
            )
            .addMapWithList(
                MarykTestProtos.CompleteMarykModel.MapWithListEntry.newBuilder().setKey(
                    "a"
                ).addValue("b").addValue("c")
            )
            .addMapWithSet(
                MarykTestProtos.CompleteMarykModel.MapWithSetEntry.newBuilder().setKey(
                    "a"
                ).addValue("b").addValue("c")
            )
            .addMapWithMap(
                MarykTestProtos.CompleteMarykModel.MapWithMapEntry.newBuilder().setKey(
                    "a"
                ).putAllValue(mapOf("b" to "c"))
            )
            .setLocation(completeObject { location }!!.asLong())
            .build()

        // Write protobuf
        val bc = ByteCollector()
        val cache = WriteCache()
        bc.reserve(
            CompleteMarykModel.Serializer.calculateProtoBufLength(completeObject, cache)
        )
        CompleteMarykModel.Serializer.writeProtoBuf(completeObject, cache, bc::write)

        val protoBytes = completeObjectProto.toByteArray()

        expect(bc.bytes!!.toHexString()) { protoBytes.toHexString() }
    }
}

private fun String.canonicalProto(): String =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n")
