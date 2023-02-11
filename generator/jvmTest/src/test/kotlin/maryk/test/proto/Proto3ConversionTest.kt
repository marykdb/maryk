@file:Suppress("EXPERIMENTAL_API_USAGE")
package maryk.test.proto

import com.google.protobuf.ByteString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import maryk.MarykTestProtos
import maryk.core.models.PropertyBaseRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykEnumEmbedded.E2
import maryk.test.models.NumericMarykModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.Test
import kotlin.test.expect

class Proto3ConversionTest {
    @Test
    fun testSimpleMarykModel(){
        // SimpleObject to convert
        val simpleObject = SimpleMarykModel.run { create(value with "testSimpleMarykModel") }
        val simpleObjectProto = MarykTestProtos.SimpleMarykModel.newBuilder().setValue("testSimpleMarykModel").build()

        // Write protobuf
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            SimpleMarykModel.Model.calculateProtoBufLength(simpleObject, cache)
        )
        SimpleMarykModel.Model.writeProtoBuf(simpleObject, cache, bc::write)

        val protoBufByteArray = simpleObjectProto.toByteArray()

        // Compare result
        expect(bc.bytes!!.toHex()) { protoBufByteArray.toHex() }
    }

    @Test
    fun testNumericMarykModel(){
        // SimpleObject to convert
        val numericObject = NumericMarykModel()
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
            NumericMarykModel.calculateProtoBufLength(numericObject, cache)
        )
        NumericMarykModel.writeProtoBuf(numericObject, cache, bc::write)

        val protoBufByteArray = numericObjectProto.toByteArray()

        // Compare result
        expect(bc.bytes!!.toHex()) { protoBufByteArray.toHex() }
    }

    @Test
    fun testCompleteMarykObject(){
        // SimpleObject to convert
        val completeObject = CompleteMarykModel(
            booleanForKey = true,
            dateForKey = LocalDate(2018, 7, 25),
            multiForKey = TypedValue(S1, "string"),
            enumEmbedded = E1,
            mapWithEnum = mapOf(
                E2 to "mapped"
            )
        )

        val completeObjectProto = MarykTestProtos.CompleteMarykModel.newBuilder()
            .setString(completeObject { string })
            .setNumber(completeObject { number }!!.toLong())
            .setBoolean(completeObject { boolean }!!)
            .setEnum(MarykTestProtos.Option.V1)
            .setDate(completeObject { date }!!.toEpochDays())
            .setDateTime(completeObject { dateTime }!!.toInstant(TimeZone.UTC).toEpochMilliseconds())
            .setTime(completeObject { time }!!.toMillisecondOfDay())
            .setFixedBytes(ByteString.copyFrom(Bytes("AAECAwQ").bytes))
            .setFlexBytes(ByteString.copyFrom(Bytes("AAECAw").bytes))
            .setReference(ByteString.copyFrom(Key<PropertyBaseRootDataModel<SimpleMarykModel>>("AAECAQAAECAQAAECAQAAEC").bytes))
            .setSubModel(MarykTestProtos.SimpleMarykModel.newBuilder().setValue("a default"))
            .setValueModel(ByteString.copyFrom(completeObject { valueModel }!!.toByteArray()))
            .addAllList(mutableListOf("ha1", "ha2", "ha3"))
            .addAllSet(mutableListOf(1, 2, 3))
            .putMap(LocalDate(2010, 11, 12).toEpochDays(), 1)
            .putMap(LocalDate(2011, 12, 13).toEpochDays(), 1)/**/
            .setMulti(MarykTestProtos.CompleteMarykModel.MultiType.newBuilder().setT1("a value"))
            .setBooleanForKey(completeObject { booleanForKey }!!)
            .setDateForKey(completeObject { dateForKey }!!.toEpochDays())
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
            CompleteMarykModel.calculateProtoBufLength(completeObject, cache)
        )
        CompleteMarykModel.writeProtoBuf(completeObject, cache, bc::write)

        val protoBytes = completeObjectProto.toByteArray()

        expect(bc.bytes!!.toHex()) { protoBytes.toHex() }
    }
}
