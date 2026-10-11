package maryk.datastore.memory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.GetUpdateHistoryRequest
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.memory.processors.AnyAddStoreAction
import maryk.datastore.memory.processors.AnyChangeStoreAction
import maryk.datastore.memory.processors.AnyDeleteStoreAction
import maryk.datastore.memory.processors.AnyGetChangesStoreAction
import maryk.datastore.memory.processors.AnyGetStoreAction
import maryk.datastore.memory.processors.AnyGetUpdatesStoreAction
import maryk.datastore.memory.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.memory.processors.AnyScanChangesStoreAction
import maryk.datastore.memory.processors.AnyScanStoreAction
import maryk.datastore.memory.processors.AnyScanUpdateHistoryStoreAction
import maryk.datastore.memory.processors.AnyScanUpdatesStoreAction
import maryk.datastore.memory.processors.processAddRequest
import maryk.datastore.memory.processors.processAdditionUpdate
import maryk.datastore.memory.processors.processChangeRequest
import maryk.datastore.memory.processors.processChangeUpdate
import maryk.datastore.memory.processors.processDeleteRequest
import maryk.datastore.memory.processors.processDeleteUpdate
import maryk.datastore.memory.processors.processGetChangesRequest
import maryk.datastore.memory.processors.processGetRequest
import maryk.datastore.memory.processors.processGetUpdatesRequest
import maryk.datastore.memory.processors.processInitialChangesUpdate
import maryk.datastore.memory.processors.processScanChangesRequest
import maryk.datastore.memory.processors.processScanRequest
import maryk.datastore.memory.processors.processScanUpdateHistoryRequest
import maryk.datastore.memory.processors.processScanUpdatesRequest
import maryk.datastore.memory.records.DataStore
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.applyTransactionMembers
import maryk.datastore.shared.asAtomicResponse
import maryk.datastore.shared.asTransactionResponse
import maryk.datastore.shared.mutationKeys
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.IsDataModelResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import maryk.core.properties.types.Bytes
import maryk.datastore.shared.rejectAtomicLimit
import maryk.datastore.shared.TransactionAbort
import maryk.datastore.shared.AtomicAbort
import maryk.datastore.shared.MAX_ATOMIC_STAGED_NODES
import maryk.datastore.shared.AtomicReceiptCodec
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.DISPATCHER
import maryk.datastore.shared.RequestExecutionKind
import maryk.datastore.shared.ReplicationTombstoneCompactor
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.requestExecutionKind

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
private data class CompactReplicationTombstonesRequest(
    override val dataModel: IsRootDataModel,
    val upToVersionInclusive: ULong,
) : IsStoreRequest<IsRootDataModel, CompactReplicationTombstonesResponse>

private object CompactReplicationTombstonesResponse : IsResponse

private typealias CompactReplicationTombstonesStoreAction = StoreAction<
    IsRootDataModel,
    CompactReplicationTombstonesRequest,
    CompactReplicationTombstonesResponse,
>

class InMemoryDataStore private constructor(
    override val keepAllVersions: Boolean = false,
    override val keepUpdateHistoryIndex: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>,
) : AbstractDataStore(dataModelsById, DISPATCHER), SnapshotVersionProvider, ReplicationTombstoneCompactor {

    override val atomicCapabilities: AtomicCapabilities = AtomicCapabilities(maxStagedNodes = MAX_ATOMIC_STAGED_NODES.toUInt())
    override val supportsAtomicRequests: Boolean = true
    private val atomicDataStores = mutableMapOf<UInt, DataStore<*>>()
    private val atomicReceipts = mutableMapOf<String, ByteArray>()

    override val transactionCapabilities = TransactionCapabilities(atomicCapabilities)
    private var transactionRevision = freshTransactionRevision()

    override suspend fun processAtomicRequest(request: AtomicRequest, version: HLC): AtomicResponse =
        processTransactionRequest(TransactionRequest(request.changeSetId, request.requests, commit = true,
            initialGuards = request.guards, initialStateChecks = true), version).asAtomicResponse()

    override suspend fun processTransactionRequest(request: TransactionRequest, version: HLC): TransactionResponse {
        val codec = AtomicReceiptCodec(dataModelsById)
        val fingerprint = codec.transactionFingerprint(request)
        atomicReceipts[request.transactionId]?.let { return codec.replayTransaction(it, fingerprint, request) }
        val revision = transactionRevision
        val staged = mutableMapOf<UInt, DataStore<IsRootDataModel>>()
        val targetKeys = request.mutationKeys().mapKeys { getDataModelId(it.key) }
        val executionVersion = if (request.commit) version else HLC(request.stagingVersion ?: version.timestamp)
        var committed = false
        var stagedNodes = 0L
        val budget: (Int) -> Unit = { count ->
            stagedNodes += count
            if (stagedNodes > MAX_ATOMIC_STAGED_NODES) rejectAtomicLimit("stagedNodes", MAX_ATOMIC_STAGED_NODES.toULong(), stagedNodes.toULong())
        }
        val updates = mutableListOf<Update<*>>()
        val fetcher: (IsRootDataModel) -> DataStore<IsRootDataModel> = { model ->
            val id = getDataModelId(model)
            @Suppress("UNCHECKED_CAST")
            val original = atomicDataStores[id] as? DataStore<IsRootDataModel>
            if (targetKeys[id].isNullOrEmpty()) original ?: DataStore(keepAllVersions, keepUpdateHistoryIndex)
            else staged.getOrPut(id) {
                original?.copyForAtomic(targetKeys[id].orEmpty(), budget)
                    ?: DataStore(keepAllVersions, keepUpdateHistoryIndex)
            }
        }
        return try {
            val result = applyTransactionMembers(request, revision, executionVersion) { member, memberVersion ->
                val response = CompletableDeferred<IsResponse>()
                val action = StoreAction(member, response)
                @Suppress("UNCHECKED_CAST")
                when (member) {
                    is AddRequest<*> -> processAddRequest(memberVersion, action as AnyAddStoreAction, fetcher) { updates += it }
                    is ChangeRequest<*> -> processChangeRequest(memberVersion, action as AnyChangeStoreAction, fetcher) { updates += it }
                    is DeleteRequest<*> -> processDeleteRequest(memberVersion, action as AnyDeleteStoreAction, fetcher) { updates += it }
                    is GetRequest<*> -> processGetRequest(action as AnyGetStoreAction, fetcher)
                    is ScanRequest<*> -> processScanRequest(action as AnyScanStoreAction, fetcher)
                    else -> error("Invalid transaction operation")
                }
                response.await() as IsDataModelResponse<*>
            }
            val receipt = codec.transactionReceipt(fingerprint, result)
            if (request.commit) {
                // No suspension between publishing models and recording the receipt.
                staged.values.forEach { it.finishAtomicCopy() }
                atomicDataStores.putAll(staged)
                atomicReceipts[request.transactionId] = receipt
                if (request.hasMutations) transactionRevision = freshTransactionRevision()
                committed = true
                observeCommittedVersion(requireNotNull(result.version))
                publishCommittedTransactionUpdates { updates.forEach { emitFlowUpdate(it) } }
            }
            result
        } catch (abort: TransactionAbort) {
            if (committed) TransactionResponse(TransactionStatus.OutcomeUnknown, message = abort.message) else abort.response
        } catch (abort: AtomicAbort) {
            if (committed) TransactionResponse(TransactionStatus.OutcomeUnknown, message = abort.message) else abort.response.asTransactionResponse()
        }
    }

    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    override suspend fun captureSnapshotVersion(): ULong = captureLocalSnapshotVersion()

    init {
        startFlows()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        this.launch {
            val dataStores = atomicDataStores

            var clock = HLC()

            storeActorHasStarted.complete(Unit)
            try {

                processStoreActions { storeAction ->
                    try {
                        if (storeAction.request.requestExecutionKind == RequestExecutionKind.Mutation) {
                            transactionRevision = freshTransactionRevision()
                            clock = nextMutationClock(
                                clock,
                                (storeAction.request as? UpdateResponse<*>)?.update?.version,
                            )
                            observeCommittedVersion(clock.timestamp)
                        }

                        val dataStoreFetcher: (IsRootDataModel) -> DataStore<IsRootDataModel> = { model: IsRootDataModel ->
                            val index = dataModelIdsByString[model.Meta.name] ?: throw DefNotFoundException(model.Meta.name)
                            @Suppress("UNCHECKED_CAST")
                            dataStores.getOrPut(index) {
                                DataStore<IsRootDataModel>(keepAllVersions, keepUpdateHistoryIndex)
                            } as DataStore<IsRootDataModel>
                        }

                        @Suppress("UNCHECKED_CAST")
                        when (storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                            is GetRequest<*> ->
                                processGetRequest(storeAction as AnyGetStoreAction, dataStoreFetcher)
                            is GetChangesRequest<*> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, dataStoreFetcher)
                            is GetUpdatesRequest<*> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, dataStoreFetcher)
                            is ScanRequest<*> ->
                                processScanRequest(storeAction as AnyScanStoreAction, dataStoreFetcher)
                            is ScanChangesRequest<*> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, dataStoreFetcher)
                            is ScanUpdateHistoryRequest<*>, is GetUpdateHistoryRequest<*> ->
                                processScanUpdateHistoryRequest(storeAction as AnyScanUpdateHistoryStoreAction, dataStoreFetcher)
                            is ScanUpdatesRequest<*> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, dataStoreFetcher)
                            is CompactReplicationTombstonesRequest -> {
                                val compactionAction = storeAction as CompactReplicationTombstonesStoreAction
                                dataStores.values.forEach {
                                    it.compactHardDeleteTombstones(compactionAction.request.upToVersionInclusive)
                                }
                                compactionAction.response.complete(
                                    CompactReplicationTombstonesResponse
                                )
                            }
                            is UpdateResponse<*> -> when(val update = (storeAction.request as UpdateResponse<*>).update) {
                                is AdditionUpdate<*> -> processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                                is ChangeUpdate<*> -> processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                                is RemovalUpdate<*> -> processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                                is InitialChangesUpdate<*> -> processInitialChangesUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher) { update -> emitFlowUpdate(update) }
                                is InitialValuesUpdate<*> -> throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
                                is OrderedKeysUpdate<*> -> throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
                                else -> throw TypeException("Unknown update type $update for datastore processing")
                            }
                            else -> throw TypeException("Unknown request type ${storeAction.request}")
                        }
                    } catch (e: CancellationException) {
                        storeAction.response.cancel(e)
                        throw e // terminate the actor
                    } catch (e: Throwable) {
                        e.rethrowIfFatal()
                        storeAction.response.completeExceptionally(e)
                    }
                }
            } finally {
                while (!storeChannel.isEmpty) {
                    val pending = storeChannel.tryReceive().getOrNull() ?: break
                    pending.response.completeExceptionally(CancellationException("Datastore closing"))
                }
            }
        }
    }

    override suspend fun compactReplicationTombstones(upToVersionInclusive: ULong) {
        val dataModel = dataModelsById.values.firstOrNull() ?: return
        execute(CompactReplicationTombstonesRequest(dataModel, upToVersionInclusive))
    }

    companion object {
        fun open(
            keepAllVersions: Boolean = false,
            keepUpdateHistoryIndex: Boolean = false,
            dataModelsById: Map<UInt, IsRootDataModel>,
        ) = InMemoryDataStore(
            keepAllVersions = keepAllVersions,
            keepUpdateHistoryIndex = keepUpdateHistoryIndex,
            dataModelsById = dataModelsById,
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun freshTransactionRevision() = Bytes(Uuid.random().toByteArray())
