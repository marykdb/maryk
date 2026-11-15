package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.requests.RequestType
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.pairs.with
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object RestorePayloadModel : RootDataModel<RestorePayloadModel>() {
    val entries by list(1u, valueDefinition = StringDefinition())
}

class RestoreVersionTest {
    private fun record(keyId: Int, versions: List<ULong> = listOf(100uL)) = DataObjectVersionedChange(
        SimpleMarykModel.key(ByteArray(16).also { it[15] = keyId.toByte() }),
        changes = versions.map { VersionedChanges(it, listOf(ObjectCreate)) },
    )

    @Suppress("UNCHECKED_CAST")
    private fun update(records: List<DataObjectVersionedChange<SimpleMarykModel>>, boundary: ULong = 102uL) =
        UpdateResponse(SimpleMarykModel, InitialChangesUpdate(boundary, records)) as UpdateResponse<IsRootDataModel>

    @Test
    fun versionGroupsRejectMixedVersionsAndDuplicateIdentities() {
        assertEquals(100uL, RestoreVersionRequest(listOf(update(listOf(record(1), record(2))))).version)
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(listOf(record(1), record(2, listOf(101uL)))))) }
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(listOf(record(1), record(1))))) }
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(listOf(record(1))), update(listOf(record(2))))) }
    }

    @Test
    fun versionGroupsRequireOneBoundedVersionPerRecord() {
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(emptyList()))) }
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(listOf(record(1, listOf(100uL, 101uL)))))) }
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(listOf(record(1)), 100uL))) }
        assertFailsWith<RequestException> { RestoreVersionRequest(listOf(update(listOf(record(1)), ULong.MAX_VALUE - 1uL))) }
    }

    @Test
    fun stateBaselineAllowsIndependentSourceVersions() {
        val records = listOf(100uL, 200uL).mapIndexed { index, version ->
            ValuesWithMetaData(
                key = RestorePayloadModel.key(ByteArray(16).also { it[15] = index.toByte() }),
                values = RestorePayloadModel.create { entries with listOf("entry") },
                firstVersion = version, lastVersion = version + 10uL, isDeleted = false,
            )
        }
        @Suppress("UNCHECKED_CAST")
        val request = RestoreVersionRequest.state(RestorePayloadModel, records as List<ValuesWithMetaData<IsRootDataModel>>)
        assertEquals(210uL, request.version)
        assertEquals(2, request.targets.getValue(RestorePayloadModel).size)
    }

    @Test
    fun stagedRequestsOwnDecodedContainersIndependently() {
        val entries = mutableListOf("before")
        val changes = mutableListOf<IsChange>(ObjectCreate, Change(RestorePayloadModel.entries.ref() with entries))
        val record = DataObjectVersionedChange(RestorePayloadModel.key(ByteArray(16)), changes = listOf(VersionedChanges(100uL, changes)))
        @Suppress("UNCHECKED_CAST")
        val updates = listOf(UpdateResponse(RestorePayloadModel, InitialChangesUpdate(101uL, listOf(record))) as UpdateResponse<IsRootDataModel>)
        val first = RestoreVersionRequest.staged(updates)
        val second = RestoreVersionRequest.staged(updates)
        entries[0] = "caller mutation"
        changes.clear()
        fun payload(request: RestoreVersionRequest): MutableList<String> {
            val owned = (request.updates.single().update as InitialChangesUpdate).changes.single()
            @Suppress("UNCHECKED_CAST")
            return owned.changes.single().changes.filterIsInstance<Change>().single().referenceValuePairs.single().value as MutableList<String>
        }
        assertEquals(listOf("before"), payload(first))
        payload(first)[0] = "first request mutation"
        assertEquals(listOf("before"), payload(second))
    }

    @Test
    fun versionRequestOwnsItsKeysAndChanges() {
        val key = SimpleMarykModel.key(ByteArray(16))
        val changes = mutableListOf<IsChange>(ObjectCreate, Change(SimpleMarykModel.ref { value } with "ha original"))
        val record = DataObjectVersionedChange(key, changes = listOf(VersionedChanges(100uL, changes)))
        @Suppress("UNCHECKED_CAST")
        val update = UpdateResponse(SimpleMarykModel, InitialChangesUpdate(101uL, listOf(record))) as UpdateResponse<IsRootDataModel>
        val request = RestoreVersionRequest(listOf(update))
        key.bytes[0] = 42
        changes[1] = Change(SimpleMarykModel.ref { value } with "ha mutated")
        val owned = (request.updates.single().update as InitialChangesUpdate).changes.single()
        assertEquals(0, owned.key.bytes[0].toInt())
        assertEquals(0, request.targets.getValue(SimpleMarykModel).single().bytes[0].toInt())
        val value = owned.changes.single().changes.filterIsInstance<Change>().single().referenceValuePairs.single().value
        assertEquals("ha original", value)
    }

    @Test
    fun admittedSizeStillChecksLowerLimitsAndEveryMutationAfterExposure() =
        checkStagedSizeAdmission { updates, bytes -> RestoreVersionRequest.staged(updates, bytes) }

    @Test
    fun privatelyDecodedSizeStillChecksLowerLimitsAndEveryMutationAfterExposure() =
        checkStagedSizeAdmission { updates, _ -> RestoreVersionRequest.staged(updates) }

    @Test
    fun stagedByteUpperBoundsFallBackForTightLimitsAndInvalidateOnExposure() =
        checkStagedSizeAdmission { updates, bytes -> RestoreVersionRequest.stagedWithByteUpperBound(updates, bytes + 128L) }

    @Test
    fun stagedRequestsCannotBypassTheHardMutationLimit() {
        val records = List(MAX_ATOMIC_OPERATIONS.toInt() + 1) { id ->
            DataObjectVersionedChange(SimpleMarykModel.key(ByteArray(16).also {
                it[14] = (id shr 8).toByte()
                it[15] = id.toByte()
            }), changes = listOf(VersionedChanges(100uL, listOf(ObjectCreate))))
        }
        assertFailsWith<RequestException> { RestoreVersionRequest.staged(listOf(update(records))) }
    }

    private fun checkStagedSizeAdmission(create: (List<UpdateResponse<IsRootDataModel>>, Long) -> RestoreVersionRequest) {
        val entries = mutableListOf("before")
        val record = DataObjectVersionedChange(
            RestorePayloadModel.key(ByteArray(16)),
            changes = listOf(VersionedChanges(100uL, listOf(ObjectCreate,
                Change(RestorePayloadModel.entries.ref() with entries)))),
        )
        @Suppress("UNCHECKED_CAST")
        val records = listOf(record) as List<DataObjectVersionedChange<IsRootDataModel>>
        val update = UpdateResponse<IsRootDataModel>(RestorePayloadModel, InitialChangesUpdate(101uL, records))
        val bytes = RestoreCodec(mapOf(RestorePayloadModel.Meta.name to RestorePayloadModel))
            .replaySize(RestorePayloadModel.Meta.name, 101uL, records).toLong()
        val request = create(listOf(update), bytes)
        val originalLimit = AtomicCapabilities(maxRequestBytes = bytes.toUInt())
        entries[0] = "caller mutation".repeat(1000)
        validateRestoreVersion(request, originalLimit.copy(maxRequestBytes = bytes.toUInt() + 256u))
        validateRestoreVersion(request, originalLimit)
        assertEquals(setOf(RequestType.Add, RequestType.Change), request.requiredRequestTypes().getValue(RestorePayloadModel))
        assertFailsWith<RequestException> {
            validateRestoreVersion(request, originalLimit.copy(maxRequestBytes = bytes.toUInt() - 1u))
        }

        val ownedRecord = (request.updates.single().update as InitialChangesUpdate).changes.single()
        @Suppress("UNCHECKED_CAST")
        val ownedEntries = ownedRecord.changes.single().changes.filterIsInstance<Change>()
            .single().referenceValuePairs.single().value as MutableList<String>
        assertEquals(listOf("before"), ownedEntries)
        ownedEntries[0] = "x".repeat(4096)
        assertFailsWith<RequestException> { validateRestoreVersion(request, originalLimit) }
        val largerLimit = originalLimit.copy(maxRequestBytes = 8192u)
        validateRestoreVersion(request, largerLimit)
        ownedEntries[0] = "x".repeat(16384)
        assertFailsWith<RequestException> { validateRestoreVersion(request, largerLimit) }
    }

    @Test
    fun authorizationMetadataKeepsMixedOperationsAndEmptyChangeGroupsPerModel() {
        val mixedRecord = DataObjectVersionedChange(RestorePayloadModel.key(ByteArray(16)), changes = listOf(VersionedChanges(100uL,
            listOf(ObjectCreate, Change(RestorePayloadModel.entries.ref() with listOf("value")), ObjectSoftDeleteChange(true)))))
        @Suppress("UNCHECKED_CAST")
        val mixed = UpdateResponse(RestorePayloadModel, InitialChangesUpdate(101uL, listOf(mixedRecord))) as UpdateResponse<IsRootDataModel>
        val empty = record(2).copy(changes = listOf(VersionedChanges(100uL, emptyList())))
        val request = RestoreVersionRequest(listOf(mixed, update(listOf(empty))))
        val expected = setOf(RequestType.Add, RequestType.Change, RequestType.Delete)
        val metadata = request.requiredRequestTypes()
        assertEquals(expected, metadata.getValue(RestorePayloadModel))
        assertEquals(setOf(RequestType.Change), metadata.getValue(SimpleMarykModel))
        @Suppress("UNCHECKED_CAST")
        val mutableTypes = metadata.getValue(RestorePayloadModel) as MutableSet<RequestType>
        mutableTypes.clear()
        assertEquals(expected, request.requiredRequestTypes().getValue(RestorePayloadModel))
    }
}
