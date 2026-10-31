package maryk.core.properties.definitions

import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.ContainsDefinitionsContext
import maryk.json.JsonReader
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val textCase = MultiTypeEnum.invoke(1u, "Text", StringDefinition())
private val firstListCase = MultiTypeEnum.invoke(2u, "FirstList", ListDefinition(valueDefinition = NumberDefinition(type = SInt32)))
private val secondListCase = MultiTypeEnum.invoke(3u, "SecondList", ListDefinition(valueDefinition = NumberDefinition(type = SInt32)))
private val continuationEnum = MultiTypeEnumDefinition("Continuation", { listOf(textCase, firstListCase, secondListCase) })
private val continuationDefinition = MultiTypeDefinition(typeEnum = continuationEnum)

private object ContinuationRecord : RootDataModel<ContinuationRecord>() {
    val typed by multiType(1u, typeEnum = continuationEnum)
}

class TransportContinuationIntegrityTest {
    @Test
    fun changingTypeDiscardsTheEarlierBranchPayload() {
        for (earlier in listOf(textCase("old"), firstListCase(mutableListOf(1)))) {
            val next = secondListCase(listOf(2))
            val bytes = continuationDefinition.toTransportByteArray(next)
            var offset = 0
            assertEquals(next, continuationDefinition.readTransportBytes(bytes.size, { bytes[offset++] }, earlierValue = earlier))
            assertEquals(bytes.size, offset)
        }
    }

    @Test
    fun repeatedModelFieldsCannotMergeAcrossTypedBranches() {
        fun encode(value: TypedValue<MultiTypeEnum<Any>, Any>): ByteArray {
            val values = ContinuationRecord.create { typed with value }
            val cache = WriteCache()
            val bytes = ByteArray(ContinuationRecord.Serializer.calculateProtoBufLength(values, cache))
            var offset = 0
            ContinuationRecord.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it })
            return bytes
        }
        val bytes = encode(firstListCase(listOf(1))) + encode(secondListCase(listOf(2)))
        var offset = 0
        val values = ContinuationRecord.Serializer.readProtoBuf(bytes.size, { bytes[offset++] })
        assertEquals(secondListCase(listOf(2)), values { typed })
    }

    @Test
    fun contextualDefinitionsPreserveSameBranchContinuations() {
        val wrappedType = ContextualValueDefinition(contextualResolver = { _: ContainsDefinitionsContext? -> continuationDefinition })
        val next = firstListCase(listOf(2))
        val bytes = continuationDefinition.toTransportByteArray(next)
        var offset = 0
        assertEquals(firstListCase(listOf(1, 2)), wrappedType.readTransportBytes(bytes.size, { bytes[offset++] },
            earlierValue = firstListCase(mutableListOf(1))))

        val list = ListDefinition(valueDefinition = StringDefinition())
        val wrappedList = ContextualSubDefinition(contextualResolver = { _: IsPropertyContext? -> list })
        val item = "new".encodeToByteArray()
        offset = 0
        assertEquals(listOf("old", "new"), wrappedList.readTransportBytes(item.size, { item[offset++] },
            earlierValue = mutableListOf("old")))
    }

    @Test
    fun modelRejectsFixedWidthWireTagsForMultiTypeMessages() {
        // An invalid fixed32 outer tag followed by an otherwise valid Text("ab") message.
        val bytes = byteArrayOf(0x0d, 0x0a, 2, 'a'.code.toByte(), 'b'.code.toByte())
        var offset = 0
        assertFailsWith<ParseException> {
            ContinuationRecord.Serializer.readProtoBuf(bytes.size, { bytes[offset++] })
        }
    }

    @Test
    fun typedJsonReaderRequiresExactlyOneTypeAndOnePayload() {
        for (text in listOf("[\"Text\",\"ab\",\"extra\"]", "[\"Text\",\"ab\",null]")) {
            val reader = JsonReader(text)
            reader.nextToken()
            assertFailsWith<ParseException> { continuationDefinition.readJson(reader) }
        }
    }
}
