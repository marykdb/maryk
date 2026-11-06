package maryk.core.protobuf

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException
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
