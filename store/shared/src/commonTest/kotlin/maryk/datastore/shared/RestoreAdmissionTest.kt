package maryk.datastore.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RestoreAdmissionTest {
    private fun request(count: Int = 1): RestoreVersionRequest {
        val records = (1..count).map { index ->
            DataObjectVersionedChange(SimpleMarykModel.key(ByteArray(16).also { it[15] = index.toByte() }),
                changes = listOf(VersionedChanges(100uL, listOf(ObjectCreate,
                    Change(SimpleMarykModel.ref { value } with "ha admitted")))))
        }
        @Suppress("UNCHECKED_CAST")
        val update = UpdateResponse(SimpleMarykModel, InitialChangesUpdate(101uL, records)) as UpdateResponse<IsRootDataModel>
        return RestoreVersionRequest(listOf(update))
    }

    @Test
    fun genericExecuteCannotBypassRestoreByteLimit() = runTest {
        val store = AdmissionStore(AtomicCapabilities(maxRequestBytes = 1u))
        try {
            assertFailsWith<RequestException> { store.restoreVersion(request()) }
            assertFailsWith<RequestException> { store.execute(request()) }
            assertEquals(0, store.restoreCalls)
        } finally { store.close() }
    }

    @Test
    fun genericExecuteCannotBypassRestoreMutationLimit() = runTest {
        val store = AdmissionStore(AtomicCapabilities(maxMutations = 1u))
        try {
            assertFailsWith<RequestException> { store.restoreVersion(request(2)) }
            assertFailsWith<RequestException> { store.execute(request(2)) }
            assertEquals(0, store.restoreCalls)
        } finally { store.close() }
    }

    @Test
    fun genericExecuteCannotBypassUnsupportedRestoreCapability() = runTest {
        val store = AdmissionStore(supported = false)
        try {
            assertFailsWith<RequestException> { store.restoreVersion(request()) }
            assertFailsWith<RequestException> { store.execute(request()) }
            assertEquals(0, store.restoreCalls)
        } finally { store.close() }
    }

    @Test
    fun bothRoutesApplyAnAdmittedRestoreExactlyOnce() = runTest {
        val store = AdmissionStore()
        try {
            store.restoreVersion(request())
            assertEquals(1, store.restoreCalls)
            assertEquals(RestoreVersionResponse, store.execute(request()))
            assertEquals(2, store.restoreCalls)
        } finally { store.close() }
    }
}

private class AdmissionStore(
    override val atomicCapabilities: AtomicCapabilities = AtomicCapabilities(),
    private val supported: Boolean = true,
) : AbstractDataStore(mapOf(1u to SimpleMarykModel), Dispatchers.Default) {
    override val keepAllVersions = true
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true
    override val supportsVersionedRestore get() = supported
    var restoreCalls = 0
        private set

    init { startFlows() }
    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions { _ -> error("Unexpected ordinary request") }
        }
    }
    override suspend fun processRestoreVersionRequest(request: RestoreVersionRequest) { restoreCalls++ }
}
