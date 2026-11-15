package maryk.core.protobuf

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyValueDefinition
import maryk.core.properties.types.TimePrecision
import maryk.core.query.filters.And
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrictProtoBufFieldsTest {
    private fun context(strict: Boolean) = RequestContext(DefinitionsContext(), OldStrictRecord).also {
        it.rejectUnknownProtoBufFields = strict
    }

    private fun encode(values: Values<NewStrictRecord>): ByteArray {
        val cache = WriteCache()
        val size = NewStrictRecord.Serializer.calculateProtoBufLength(values, cache)
        var index = 0
        return ByteArray(size).also { bytes -> NewStrictRecord.Serializer.writeProtoBuf(values, cache, { bytes[index++] = it }) }
    }

    private fun read(bytes: ByteArray, strict: Boolean): Values<OldStrictRecord> {
        var index = 0
        return OldStrictRecord.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, context(strict))
    }

    @Test
    fun strictMapEntriesRejectUnknownFieldsWhileNormalReadsPreserveCompatibility() {
        val definition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
        val bytes = byteArrayOf(0x0a, 1, 0x6b, 0x12, 1, 0x76, 0x18, 1)
        fun read(strict: Boolean): Map<String, String> {
            var offset = 0
            return definition.readTransportBytes(bytes.size, { bytes[offset++] }, context(strict))
        }
        assertEquals(mapOf("k" to "v"), read(false))
        assertFailsWith<ParseException> { read(true) }
    }

    @Test
    fun strictCollectionEnvelopesRejectUnknownFieldsWhileNormalReadsPreserveCompatibility() {
        val definition = ContextualPropertyValueDefinition<RequestContext, RequestContext, List<String>>(
            contextualResolver = { ListDefinition(valueDefinition = StringDefinition()) },
        )
        val bytes = byteArrayOf(0x0a, 1, 0x61, 0x10, 1)
        fun read(strict: Boolean): List<String> {
            var offset = 0
            return definition.readTransportBytes(bytes.size, { bytes[offset++] }, context(strict))
        }
        assertEquals(listOf("a"), read(false))
        assertFailsWith<ParseException> { read(true) }
    }

    @Test
    fun strictNanosMessagesRejectUnknownFieldsWhileNormalReadsPreserveCompatibility() {
        val definition = DateTimeDefinition(precision = TimePrecision.NANOS)
        val bytes = byteArrayOf(0x08, 2, 0x10, 0, 0x18, 9)
        fun read(strict: Boolean): LocalDateTime {
            var offset = 0
            return definition.readTransportBytes(bytes.size, { bytes[offset++] }, context(strict))
        }
        assertEquals(LocalDateTime(1970, 1, 1, 0, 0, 2), read(false))
        assertFailsWith<ParseException> { read(true) }
    }

    @Test
    fun strictFilterListsRejectUnknownFieldsWhileNormalReadsPreserveCompatibility() {
        val value = And()
        val cache = WriteCache()
        val size = And.Serializer.calculateObjectProtoBufLength(value, cache, context(false))
        val encoded = ByteArray(size)
        var offset = 0
        And.Serializer.writeObjectProtoBuf(value, cache, { encoded[offset++] = it }, context(false))
        val bytes = encoded + byteArrayOf(0x10, 1)
        fun read(strict: Boolean): And {
            var position = 0
            return And.Serializer.readProtoBuf(bytes.size, { bytes[position++] }, context(strict)).toDataObject()
        }
        assertEquals(value, read(false))
        assertFailsWith<ParseException> { read(true) }
    }

    @Test
    fun normalTransportStillSkipsUnknownFields() {
        val bytes = encode(NewStrictRecord.create { value with "known"; additional with "new" })
        val values = read(bytes, false)
        assertEquals("known", values { value })
        assertEquals(1, values.size)
    }

    @Test
    fun strictTransportRejectsUnknownRootAndNestedFields() {
        val root = encode(NewStrictRecord.create { value with "known"; additional with "new" })
        assertFailsWith<ParseException> { read(root, true) }
        val nested = encode(NewStrictRecord.create {
            value with "known"
            details with NewStrictDetails.create { value with "nested"; additional with "must not disappear" }
        })
        assertFailsWith<ParseException> { read(nested, true) }
        val compatible = read(nested, false)
        assertEquals(OldStrictDetails.create { value with "nested" }, compatible { details })
    }

    @Test
    fun strictTransportRejectsUnknownEmptyCollectionMarkers() {
        val bytes = encode(NewStrictRecord.create { value with "known"; additionalItems with emptyList() })
        assertFailsWith<ParseException> { read(bytes, true) }
        assertEquals(1, read(bytes, false).size)
    }

    @Test
    fun nestedMessageBoundsDoNotConsumeTheFollowingSibling() {
        // details declares three bytes, but its string incorrectly declares two payload bytes.
        val malformed = byteArrayOf(0x12, 3, 0x0a, 2, 0x61, 0x0a, 1, 0x62)
        var offset = 0
        assertFailsWith<ParseException> {
            OldStrictRecord.Serializer.readProtoBuf(malformed.size, { malformed[offset++] }, context(true))
        }
        assertEquals(4, offset)

        val valid = byteArrayOf(0x12, 3, 0x0a, 1, 0x61, 0x0a, 1, 0x62)
        offset = 0
        val decoded = OldStrictRecord.Serializer.readProtoBuf(valid.size, { valid[offset++] }, context(true))
        assertEquals(valid.size, offset)
        assertEquals("b", decoded { value })
        assertEquals("a", decoded { details }?.get { value })
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun strictEncodingCannotDiscardUnknownProvidedFields() {
        val values = NewStrictRecord.create { value with "known"; additional with "new" } as Values<OldStrictRecord>
        assertFailsWith<ParseException> { OldStrictRecord.Serializer.calculateProtoBufLength(values, WriteCache(), context(true)) }
        assertFailsWith<ParseException> { OldStrictRecord.Serializer.writeProtoBuf(values, WriteCache(), {}, context(true)) }
        val nested = NewStrictRecord.create {
            value with "known"
            details with NewStrictDetails.create { value with "nested"; additional with "new" }
        } as Values<OldStrictRecord>
        assertFailsWith<ParseException> { OldStrictRecord.Serializer.calculateProtoBufLength(nested, WriteCache(), context(true)) }
    }
}

private object OldStrictDetails : DataModel<OldStrictDetails>() {
    val value by string(1u)
}
private object NewStrictDetails : DataModel<NewStrictDetails>() {
    val value by string(1u)
    val additional by string(2u, required = false)
}
private object OldStrictRecord : RootDataModel<OldStrictRecord>() {
    val value by string(1u)
    val details by embed(2u, required = false, dataModel = { OldStrictDetails })
}
private object NewStrictRecord : RootDataModel<NewStrictRecord>() {
    val value by string(1u)
    val details by embed(2u, required = false, dataModel = { NewStrictDetails })
    val additional by string(3u, required = false)
    val additionalItems by list(4u, required = false, valueDefinition = StringDefinition())
}
