package maryk.test.proto

import com.google.protobuf.ByteString
import maryk.CompleteMarykModel
import maryk.MarykEnum
import maryk.MarykEnumEmbedded
import maryk.MarykTestProtos
import maryk.SimpleMarykModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Date
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class Proto3ConversionTest {
    @Test
    fun testSimpleMarykModel(){
        // SimpleObject to convert
        val simpleObject = SimpleMarykModel("testSimpleMarykModel")
        val simpleObjectProto = MarykTestProtos.SimpleMarykModel.newBuilder().setValue("testSimpleMarykModel").build()

        // Write protobuf
        val bc = ByteCollector()
        val cache = WriteCache()
        bc.reserve(
            SimpleMarykModel.calculateProtoBufLength(simpleObject, cache)
        )
        SimpleMarykModel.writeProtoBuf(simpleObject, cache, bc::write)

        val protoBufByteArray = simpleObjectProto.toByteArray()

        // Compare result
        protoBufByteArray.toHex() shouldBe bc.bytes!!.toHex()
    }

    @Test
    fun testCompleteMarykObject(){
        // SimpleObject to convert
        val completeObject = CompleteMarykModel(
            booleanForKey = true,
            dateForKey = Date(2018, 7, 25),
            multiForKey = TypedValue(MarykEnum.O1, "string"),
            enumEmbedded = MarykEnumEmbedded.E1,
            mapWithEnum = mapOf(
                MarykEnumEmbedded.E2 to "mapped"
            )
        )

        val completeObjectProto = MarykTestProtos.CompleteMarykModel.newBuilder()
            .setString(completeObject { string })
            .setNumber(completeObject { number }!!.toLong())
            .setBoolean(completeObject { boolean }!!)
            .setEnum(MarykTestProtos.MarykEnum.O1)
            .setDate(completeObject { date }!!.epochDay)
            .setDateTime(completeObject { dateTime }!!.toEpochMilli())
            .setTime(completeObject { time }!!.toMillisOfDay())
            .setFixedBytes(ByteString.copyFrom(Bytes("AAECAwQ").bytes))
            .setFlexBytes(ByteString.copyFrom(Bytes("AAECAw").bytes))
            .setReference(ByteString.copyFrom(Key<SimpleMarykModel>("AAECAQAAECAQAAECAQAAEC").bytes))
            .setSubModel(MarykTestProtos.SimpleMarykModel.newBuilder().setValue("a default"))
            .setValueModel(ByteString.copyFrom(completeObject { valueModel }!!.toByteArray()))
            .addAllList(mutableListOf("ha1", "ha2", "ha3"))
            .addAllSet(mutableListOf(1, 2, 3))
            .putMap(Date(2010, 11, 12).epochDay, 1)
            .putMap(Date(2011, 12, 13).epochDay, 1)
            .setMulti(MarykTestProtos.CompleteMarykModel.MultiType.newBuilder().setO1("a value"))
            .setBooleanForKey(completeObject { booleanForKey }!!)
            .setDateForKey(completeObject { dateForKey }!!.epochDay)
            .setMultiForKey(
                MarykTestProtos.CompleteMarykModel.MultiForKeyType.newBuilder().setO1("string")
            )
            .setEnumEmbedded(
                MarykTestProtos.CompleteMarykModel.MarykEnumEmbedded.E1
            )
            .addMapWithEnum(
                MarykTestProtos.CompleteMarykModel.MapWithEnumEntry.newBuilder().setKey(
                    MarykTestProtos.CompleteMarykModel.MarykEnumEmbedded.E2
                ).setValue("mapped")
            )
            .build()

        // Write protobuf
        val bc = ByteCollector()
        val cache = WriteCache()
        bc.reserve(
            CompleteMarykModel.calculateProtoBufLength(completeObject, cache)
        )
        CompleteMarykModel.writeProtoBuf(completeObject, cache, bc::write)

        val protoBytes = completeObjectProto.toByteArray()

        protoBytes.toHex() shouldBe bc.bytes!!.toHex()
    }
}
