package maryk.datastore.remote

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.createRequestContext
import maryk.core.query.pairs.with
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.RequestContext
import maryk.core.query.DefinitionsContext
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.shared.RestoreVersionRequest
import maryk.datastore.shared.RestoreVersionTransportCodec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object WireRestoreModel : RootDataModel<WireRestoreModel>() {
    val entries by list(1u, valueDefinition = StringDefinition())
}

private object WireRestoreChild : DataModel<WireRestoreChild>() {
    val title by string(1u)
}

private object WireRestoreOtherModel : RootDataModel<WireRestoreOtherModel>() {
    val child by embed(1u, dataModel = { WireRestoreChild })
}

class RestoreVersionTransportCodecTest {
    private val maxBytes = 16 * 1024 * 1024
    private val entries = listOf("alpha", "β", "🙂", "")

    private fun update() = UpdateResponse(WireRestoreModel, InitialChangesUpdate(101uL, listOf(
        DataObjectVersionedChange(WireRestoreModel.key(ByteArray(16)), changes = listOf(VersionedChanges(100uL, listOf(
            ObjectCreate, Change(WireRestoreModel.entries.ref() with entries),
        )))),
    )))

    @Suppress("UNCHECKED_CAST")
    private fun request() = RestoreVersionRequest(listOf(update() as UpdateResponse<IsRootDataModel>))

    private fun payload(request: RestoreVersionRequest): MutableList<String> {
        val record = (request.updates.single().update as InitialChangesUpdate).changes.single()
        @Suppress("UNCHECKED_CAST")
        return record.changes.single().changes.filterIsInstance<Change>().single().referenceValuePairs.single().value as MutableList<String>
    }

    @Test
    fun sharedCodecKeepsTheExistingEnvelopeBytesAndOwnsDecodedValues() {
        val oldBytes = RemoteStoreCodec.encode(LegacyRestoreVersion.Serializer, LegacyRestoreVersion(listOf(update())), WireRestoreModel.createRequestContext())
        val bytes = RestoreVersionTransportCodec.encode(request(), WireRestoreModel.createRequestContext(), maxBytes)
        assertContentEquals(oldBytes, bytes)
        val first = RestoreVersionTransportCodec.decode(bytes, WireRestoreModel.createRequestContext(), maxBytes)
        val second = RestoreVersionTransportCodec.decode(bytes, WireRestoreModel.createRequestContext(), maxBytes)
        bytes.fill(0)
        assertEquals(100uL, first.version)
        assertEquals(WireRestoreModel.key(ByteArray(16)), first.targets.getValue(WireRestoreModel).single())
        assertEquals(entries, payload(first))
        payload(first)[0] = "changed"
        assertEquals(entries, payload(second))
    }

    @Test
    fun strictDecodeRejectsUnknownTrailingFieldsTruncationAndSizeViolations() {
        val bytes = RestoreVersionTransportCodec.encode(request(), WireRestoreModel.createRequestContext(), maxBytes)
        for (bad in listOf(bytes + byteArrayOf(0x10, 1), bytes.copyOf(bytes.size - 1))) {
            assertFailsWith<IllegalStateException> {
                RestoreVersionTransportCodec.decode(bad, WireRestoreModel.createRequestContext(), maxBytes)
            }
        }
        assertFailsWith<IllegalStateException> {
            RestoreVersionTransportCodec.decode(bytes, WireRestoreModel.createRequestContext(), bytes.size - 1)
        }
        assertFailsWith<IllegalStateException> {
            RestoreVersionTransportCodec.encode(request(), WireRestoreModel.createRequestContext(), bytes.size - 1)
        }
        assertFailsWith<RequestException> {
            RestoreVersionTransportCodec.decode(bytes, WireRestoreModel.createRequestContext(), maxBytes, AtomicCapabilities(maxRequestBytes = 1u))
        }
    }

    @Test
    fun decodedEnvelopesStillRequireValidVersionGroups() {
        val invalid = LegacyRestoreVersion(listOf(UpdateResponse(WireRestoreModel, InitialChangesUpdate<WireRestoreModel>(101uL, emptyList()))))
        val bytes = RemoteStoreCodec.encode(LegacyRestoreVersion.Serializer, invalid, WireRestoreModel.createRequestContext())
        assertFailsWith<RequestException> {
            RestoreVersionTransportCodec.decode(bytes, WireRestoreModel.createRequestContext(), maxBytes)
        }
    }

    @Test
    fun mixedModelGroupsKeepTheirNestedPayloadsAndEncoding() {
        val values = WireRestoreOtherModel.create { child with { title with "nested" } }
        val other = UpdateResponse(WireRestoreOtherModel, InitialChangesUpdate(101uL, listOf(
            DataObjectVersionedChange(WireRestoreOtherModel.key(ByteArray(16)), changes = listOf(
                VersionedChanges(100uL, listOf(ObjectCreate) + values.toChanges()),
            )),
        )))
        val updates = listOf(update(), other)
        fun context() = RequestContext(DefinitionsContext(mutableMapOf(
            WireRestoreModel.Meta.name to DataModelReference(WireRestoreModel),
            WireRestoreOtherModel.Meta.name to DataModelReference(WireRestoreOtherModel),
        )))
        val bytes = RemoteStoreCodec.encode(LegacyRestoreVersion.Serializer, LegacyRestoreVersion(updates), context())
        val decoded = RestoreVersionTransportCodec.decode(bytes, context(), maxBytes)
        assertEquals(setOf(WireRestoreModel, WireRestoreOtherModel), decoded.targets.keys)
        assertContentEquals(bytes, RestoreVersionTransportCodec.encode(decoded, context(), maxBytes))
    }
}
