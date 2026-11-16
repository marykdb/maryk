package maryk.datastore.indexeddb

import maryk.datastore.shared.StateBackupAllocationCollector
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import maryk.datastore.shared.migration.legacyIncrementingMapProofKey
import maryk.datastore.indexeddb.processors.keyPrefixUpperBound

import maryk.datastore.shared.RestoreVersionRequest
import maryk.datastore.shared.applyRestoreVersion
import maryk.datastore.indexeddb.processors.createUniqueRowKey
import maryk.datastore.indexeddb.processors.addHistoricUniqueRows
import maryk.datastore.indexeddb.processors.IndexedDbUniqueRow
import maryk.datastore.indexeddb.processors.put
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.ProcessResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.GetUpdateHistoryRequest
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.UpdateResponse
import maryk.datastore.indexeddb.processors.AddStoreAction
import maryk.datastore.indexeddb.processors.ChangeStoreAction
import maryk.datastore.indexeddb.processors.CurrentStateStoragePlan
import maryk.datastore.indexeddb.processors.DeleteStoreAction
import maryk.datastore.indexeddb.processors.GetChangesStoreAction
import maryk.datastore.indexeddb.processors.GetStoreAction
import maryk.datastore.indexeddb.processors.GetUpdatesStoreAction
import maryk.datastore.indexeddb.processors.ProcessUpdateResponseStoreAction
import maryk.datastore.indexeddb.processors.ScanChangesStoreAction
import maryk.datastore.indexeddb.processors.ScanStoreAction
import maryk.datastore.indexeddb.processors.ScanUpdateHistoryStoreAction
import maryk.datastore.indexeddb.processors.ScanUpdatesStoreAction
import maryk.datastore.indexeddb.processors.addHistoricIndexRows
import maryk.datastore.indexeddb.processors.createIndexKeyPrefix
import maryk.datastore.indexeddb.processors.createHardDeleteHistoryRowKey
import maryk.datastore.indexeddb.processors.createStoragePlan
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshotRecord
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.processAddRequest
import maryk.datastore.indexeddb.processors.processChangeRequest
import maryk.datastore.indexeddb.processors.processDeleteRequest
import maryk.datastore.indexeddb.processors.processGetChangesRequest
import maryk.datastore.indexeddb.processors.DecodedIndexedDbSnapshot
import maryk.datastore.indexeddb.processors.PREPARED_SNAPSHOT_MIN_BYTES
import maryk.datastore.indexeddb.processors.prepareNarrowSnapshot
import maryk.datastore.indexeddb.processors.isNarrowSnapshotSelection
import maryk.datastore.indexeddb.processors.requiredReadSelection
import maryk.datastore.indexeddb.processors.processGetRequest
import maryk.datastore.indexeddb.processors.processGetUpdatesRequest
import maryk.datastore.indexeddb.processors.processScanChangesRequest
import maryk.datastore.indexeddb.processors.processScanRequest
import maryk.datastore.indexeddb.processors.processScanUpdateHistoryRequest
import maryk.datastore.indexeddb.processors.processScanUpdatesRequest
import maryk.datastore.indexeddb.processors.processUpdateResponse
import maryk.datastore.indexeddb.processors.isHardDeleteTombstone
import maryk.datastore.indexeddb.processors.isUnserializableChangeLogMarker
import maryk.datastore.indexeddb.processors.isUpdateHistoryRowKey
import maryk.datastore.indexeddb.processors.readInvertedVersion
import maryk.datastore.indexeddb.processors.readTrailingVersion
import maryk.datastore.indexeddb.processors.readTrailingInvertedVersion
import maryk.datastore.indexeddb.processors.toBigEndianBytes
import kotlinx.coroutines.CompletableDeferred
import kotlin.uuid.Uuid
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.applyTransactionMembers
import maryk.datastore.shared.asAtomicResponse
import maryk.datastore.shared.asTransactionResponse
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicFailure
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.shared.TransactionAbort
import maryk.datastore.shared.AtomicAbort
import maryk.datastore.shared.MAX_ATOMIC_WORK_BYTES
import maryk.datastore.shared.MAX_ATOMIC_SCAN_ROWS
import maryk.datastore.shared.AtomicReceiptCodec
import maryk.datastore.shared.AtomicMutationContext
import maryk.datastore.shared.currentAtomicContext
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.DISPATCHER
import maryk.datastore.shared.RequestExecutionKind
import maryk.datastore.shared.ReplicationTombstoneCompactor
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.requestExecutionKind
import maryk.datastore.shared.updates.Update
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

internal var hardDeleteTombstoneMigrationBeforeWrite: (suspend (UInt, ByteArray, ULong) -> Unit)? = null
internal var replicationTombstoneCompactionAfterScan: (suspend (UInt) -> Unit)? = null

class IndexedDbDataStore private constructor(
    byteStore: IndexedDbByteStore,
    override val keepAllVersions: Boolean = false,
    override val keepUpdateHistoryIndex: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>,
    internal val sensitiveFields: IndexedDbSensitiveFieldSupport,
) : AbstractDataStore(dataModelsById, DISPATCHER), SnapshotVersionProvider, ReplicationTombstoneCompactor {

    private val nativeByteStore = byteStore
    internal val supportsBatchedIndexReads: Boolean get() = nativeByteStore is IndexedDbBatchReadStore
    internal val byteStore: IndexedDbByteStore = AtomicAwareIndexedDbByteStore(byteStore, this)
    // Startup callbacks run under the startup fence before this successful-open baseline is captured.
    private var schemaSnapshot: IndexedDbSchemaSnapshot? = null

    private suspend fun isSchemaCurrent(): Boolean = schemaSnapshot?.matches(nativeByteStore) != false

    override val atomicCapabilities: AtomicCapabilities? = if (nativeByteStore is AtomicIndexedDbByteStore)
        AtomicCapabilities(maxWorkBytes = MAX_ATOMIC_WORK_BYTES.toUInt(), maxScanRows = MAX_ATOMIC_SCAN_ROWS.toUInt()) else null
    override val supportsAtomicRequests: Boolean get() = nativeByteStore is AtomicIndexedDbByteStore

    override val transactionCapabilities = atomicCapabilities?.let(::TransactionCapabilities)

    override val supportsStateBackup get() = supportsAtomicRequests

    override suspend fun withStateBackupReadLock(block: suspend () -> Unit) =
        nativeByteStore.transaction(setOf("meta"), IndexedDbTransactionMode.READWRITE) { block() }

    override val supportsVersionedRestore get() = supportsAtomicRequests

    override suspend fun processRestoreVersionRequest(request: RestoreVersionRequest) {
        nativeByteStore.transaction(setOf("meta", CommitJournalStoreName), IndexedDbTransactionMode.READWRITE) {
            journalCursorMutex.withLock {
                if (!isSchemaCurrent()) throw RequestException(StaleSchemaMessage)
                drainExternalCommitsLocked()
                validateJournalConsumersLocked()
                val persisted = nativeByteStore.get("meta", CommitClockMetadataKey)?.readBigEndianULong() ?: 0uL
                mutationClock = nextMutationClock(HLC(), maxOf(persisted, request.version))
                journalSequence = 0u
                val staged = StagedIndexedDbByteStore(nativeByteStore)
                val atomic = AtomicMutationContext(this@IndexedDbDataStore, staged)
                withContext(atomic) {
                    applyRestoreVersion(request,
                        read = { model, key ->
                            val response = CompletableDeferred<ValuesResponse<IsRootDataModel>>()
                            processGetRequest(StoreAction(model.get(key, filterSoftDeleted = false), response))
                            response.await().values.singleOrNull()
                        },
                        clearUnique = { claims -> claims.forEach { claim ->
                            val id = getDataModelId(claim.model)
                            for (value in sensitiveFields.mapUniqueValueByteCandidates(id, claim.qualifier, claim.encodedValue)) {
                                val key = createUniqueRowKey(claim.qualifier, value)
                                if (byteStore.get("u:$id", key)?.contentEquals(claim.key.bytes) == true) byteStore.delete("u:$id", key)
                            }
                        } },
                        apply = { update ->
                            val response = CompletableDeferred<ProcessResponse<IsRootDataModel>>()
                            processUpdateResponse(StoreAction(update, response))
                            response.await()
                        },
                        installUnique = { claims -> claims.forEach { claim ->
                            val id = getDataModelId(claim.model)
                            val candidates = sensitiveFields.mapUniqueValueByteCandidates(id, claim.qualifier, claim.encodedValue)
                                .map { createUniqueRowKey(claim.qualifier, it) }
                            for (key in candidates) {
                                val existing = byteStore.get("u:$id", key)
                                if (existing != null && !existing.contentEquals(claim.key.bytes)) throw RequestException("Restore conflicts with existing unique ownership")
                            }
                            byteStore.put("u:$id", candidates.first(), claim.key.bytes)
                            if (keepAllVersions) {
                                val operations = mutableListOf<IndexedDbWriteOperation>()
                                operations.addHistoricUniqueRows("hu:$id", "huk:$id", listOf(IndexedDbUniqueRow(candidates.first(), claim.key.bytes, claim.qualifier, candidates)), request.version, active = true)
                                byteStore.writeBatch(operations)
                            }
                        } },
                    )
                }
                for ((key, firstVersion) in request.stateCreationVersions) {
                    val store = "k:${getDataModelId(request.dataModel)}"
                    val snapshot = requireNotNull(staged.get(store, key.bytes)).copyOf()
                    val meta = decodeRecordMeta(snapshot)
                    encodeRecordMeta(meta.copy(firstVersion = firstVersion)).copyInto(snapshot)
                    staged.put(store, key.bytes, snapshot)
                    val id = getDataModelId(request.dataModel)
                    val collector = StateBackupAllocationCollector(request.dataModel)
                    for ((reference, maximum) in request.stateAllocations[key].orEmpty()) {
                        val metadataKey = incrementingMapWatermarkPrefix(id, key.bytes) + reference.bytes
                        staged.get("meta", metadataKey)?.let { collector.add(reference.bytes, it) }
                        collector.add(reference.bytes, maximum.bytes)
                    }
                    for ((reference, maximum) in collector.snapshot()) staged.put("meta",
                        incrementingMapWatermarkPrefix(id, key.bytes) + reference.bytes, maximum.bytes)
                    staged.put("meta", legacyIncrementingMapProofKey(id, key.bytes), byteArrayOf(1))
                }
                staged.put("meta", TransactionRevisionKey, Uuid.random().toByteArray())
                nativeByteStore.writeBatch(staged.operations)
                staged.journalKey?.let { lastJournalKey = it }
                observeCommittedVersion(mutationClock.timestamp)
                publishCommittedTransactionUpdates { atomic.updates.forEach { emitFlowUpdate(it) } }
                nativeByteStore.signalCommittedUpdate()
            }
        }
    }

    override suspend fun processAtomicRequest(request: AtomicRequest, version: HLC): AtomicResponse =
        processTransactionRequest(TransactionRequest(request.changeSetId, request.requests, commit = true,
            initialGuards = request.guards, initialStateChecks = true), version).asAtomicResponse()

    override suspend fun processTransactionRequest(request: TransactionRequest, version: HLC): TransactionResponse =
        nativeByteStore.transaction(setOf("meta", CommitJournalStoreName), IndexedDbTransactionMode.READWRITE) {
            journalCursorMutex.withLock {
                val codec = AtomicReceiptCodec(dataModelsById)
                val fingerprints = codec.transactionFingerprints(request)
                val fingerprint = fingerprints.current
                val receiptKey = "maryk.transaction/".encodeToByteArray() + request.transactionId.encodeToByteArray()
                nativeByteStore.get("meta", receiptKey)?.let { return@withLock codec.replayTransaction(it, fingerprints, request) }
                if (!isSchemaCurrent()) {
                    return@withLock TransactionResponse(
                        TransactionStatus.Aborted,
                        message = StaleSchemaMessage,
                        failure = AtomicFailure(AtomicFailureCode.InvalidState),
                    )
                }
                drainExternalCommitsLocked()
                validateJournalConsumersLocked()
                val persistedClock = nativeByteStore.get("meta", CommitClockMetadataKey)?.readBigEndianULong() ?: 0uL
                val revision = Bytes(nativeByteStore.get("meta", TransactionRevisionKey) ?: byteArrayOf(0))
                val atomicVersion = if (request.commit) nextMutationClock(version, persistedClock) else HLC(request.stagingVersion ?: nextMutationClock(version, persistedClock).timestamp)
                mutationClock = atomicVersion
                journalSequence = 0u
                val staged = StagedIndexedDbByteStore(nativeByteStore)
                val atomic = AtomicMutationContext(this@IndexedDbDataStore, staged)
                var committed = false
                try {
                    val result = withContext(atomic) {
                        applyTransactionMembers(request, revision, atomicVersion) { member, memberVersion ->
                            val response = CompletableDeferred<IsResponse>()
                            val action = StoreAction(member, response)
                            @Suppress("UNCHECKED_CAST")
                            when (member) {
                                is AddRequest<*> -> processAddRequest(memberVersion, action as AddStoreAction<IsRootDataModel>)
                                is ChangeRequest<*> -> processChangeRequest(memberVersion, action as ChangeStoreAction<IsRootDataModel>)
                                is DeleteRequest<*> -> processDeleteRequest(memberVersion, action as DeleteStoreAction<IsRootDataModel>)
                                is GetRequest<*> -> processGetRequest(action as GetStoreAction<IsRootDataModel>)
                                is ScanRequest<*> -> processScanRequest(action as ScanStoreAction<IsRootDataModel>)
                                else -> error("Invalid transaction member")
                            }
                            response.await() as IsDataModelResponse<*>
                        }
                    }
                    val receipt = codec.transactionReceipt(fingerprint, result)
                    if (!request.commit) return@withLock result
                    staged.put("meta", receiptKey, receipt)
                    if (request.hasMutations) staged.put("meta", TransactionRevisionKey, Uuid.random().toByteArray())
                    nativeByteStore.writeBatch(staged.operations)
                    committed = true
                    staged.journalKey?.let { lastJournalKey = it }
                    observeCommittedVersion(requireNotNull(result.version))
                    publishCommittedTransactionUpdates { atomic.updates.forEach { emitFlowUpdate(it) } }
                    nativeByteStore.signalCommittedUpdate()
                    result
                } catch (abort: TransactionAbort) {
                    if (committed) TransactionResponse(TransactionStatus.OutcomeUnknown, message = abort.message) else abort.response
                } catch (abort: AtomicAbort) {
                    if (committed) TransactionResponse(TransactionStatus.OutcomeUnknown, message = abort.message) else abort.response.asTransactionResponse()
                }
            }
        }

    init {
        (nativeByteStore as? IndexedDbLifecycleAware)?.setCloseListener {
            CoroutineScope(DISPATCHER + SupervisorJob()).launch { closeInvalidatedByteStore() }
        }
    }
    private val indexedDbModelsById = dataModelsById
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    override suspend fun captureSnapshotVersion(): ULong = reserveSnapshotVersion { localVersion ->
        byteStore.transaction(setOf("meta"), IndexedDbTransactionMode.READWRITE) { store ->
            val persistedVersion = store.get("meta", CommitClockMetadataKey)?.readBigEndianULong() ?: 0uL
            val latestVersion = maxOf(localVersion, persistedVersion)
            val snapshotVersion = if (latestVersion == ULong.MAX_VALUE) latestVersion else latestVersion + 1uL
            store.put("meta", CommitClockMetadataKey, snapshotVersion.toBigEndianBytes())
            snapshotVersion
        }
    }

    private var activeUpdateListeners = 0

    override fun onUpdateListenerAdded(dataModelId: UInt) { activeUpdateListeners++ }
    override fun onUpdateListenerRemoved(dataModelId: UInt) { activeUpdateListeners-- }

    override suspend fun onBeforeReadBoundary(isFlowSnapshot: Boolean) {
        if (isFlowSnapshot) {
            pollExternalCommits()
        } else if (activeUpdateListeners > 0) {
            val previous = lastDrainedJournalClock
            val now = byteStore.currentEpochMillis()
            if (previous == null || now < previous.acknowledgedAtMillis ||
                now - previous.acknowledgedAtMillis >= JournalConsumerTimeoutMillis / 2uL) {
                pollExternalCommits()
                return
            }
            // Every logical journal commit advances the persisted clock. A read
            // with no new commits needs neither a writer fence nor another ack.
            val current = nativeByteStore.getBatch("meta", listOf(CommitClockMetadataKey, SchemaRevisionMetadataKey))
            if (schemaSnapshot?.matches(nativeByteStore, current[1]) == false) {
                val failure = StaleIndexedDbSchemaException()
                failAllListeners(failure)
                throw failure
            }
            if (externalCommitPending || !previous.bytes.contentEquals(current[0])) pollExternalCommits()
        }
    }

    private class JournalClockSnapshot(
        val bytes: ByteArray?,
        val journalEmpty: Boolean,
        val acknowledgedAtMillis: ULong,
    )
    private class MutationJournalScope(
        val caughtUp: Boolean,
        val journalWasEmpty: Boolean,
        var prepared: Boolean = false,
        var soleConsumer: Boolean = false,
        var canPruneWithCommit: Boolean = false,
    )
    private var lastDrainedJournalClock: JournalClockSnapshot? = null
    private var mutationJournalScope: MutationJournalScope? = null

    private var mutationClock = HLC()
    private var journalSequence = 0u
    private var lastJournalKey: ByteArray? = CommitJournalStartCursor
    private val journalCursorMutex = Mutex()
    private var externalCommitListenerReady = false
    private var externalCommitPending = false
    private var journalPollingJob: Job? = null
    private var externalCommitDrainJob: Job? = null
    private val journalConsumerKey = byteStore.contextId().encodeToByteArray()

    internal suspend fun commitIndexedDbUpdate(
        operations: MutableList<IndexedDbWriteOperation>,
        update: Update<out IsRootDataModel>,
        journalPayload: ByteArray? = null,
    ) {
        currentAtomicContext(this)?.let { atomic ->
            val staged = atomic.transaction as StagedIndexedDbByteStore
            val persistedVersion = maxOf(mutationClock.timestamp, update.version)
            val journalKey = createCommitJournalKey(persistedVersion, journalSequence++)
            operations += IndexedDbWriteOperation.Put("meta", CommitClockMetadataKey, persistedVersion.toBigEndianBytes())
            operations += IndexedDbWriteOperation.Put(CommitJournalStoreName, journalKey,
                encodeCommitJournalEntry(getDataModelId(update.dataModel), update, journalPayload))
            operations += journalConsumerOperation(journalKey)
            staged.writeBatch(operations)
            staged.journalKey = journalKey
            atomic.updates += update
            return
        }
        journalCursorMutex.withLock {
            try {
                val preparation = mutationJournalScope
                if (preparation?.prepared != true) {
                    if (preparation?.caughtUp != true) drainExternalCommitsLocked()
                    val soleConsumer = validateJournalConsumersLocked()
                    preparation?.let {
                        it.soleConsumer = soleConsumer
                        it.canPruneWithCommit = soleConsumer && it.caughtUp && it.journalWasEmpty
                    }
                    // The request retains its fenced writer lease: no peer can
                    // add commits or consumers between records in this scope.
                    preparation?.prepared = true
                }
                val persistedVersion = maxOf(mutationClock.timestamp, update.version)
                val journalKey = createCommitJournalKey(persistedVersion, journalSequence++)
                val clockBytes = persistedVersion.toBigEndianBytes()
                operations += IndexedDbWriteOperation.Put("meta", CommitClockMetadataKey, clockBytes)
                operations += IndexedDbWriteOperation.Put(
                    CommitJournalStoreName,
                    journalKey,
                    encodeCommitJournalEntry(getDataModelId(update.dataModel), update, journalPayload),
                )
                val acknowledgedAt = byteStore.currentEpochMillis()
                operations += journalConsumerOperation(journalKey, acknowledgedAt)
                val pruneWithCommit = preparation?.canPruneWithCommit == true
                if (pruneWithCommit) {
                    // Preparation proved a sole consumer and an empty journal.
                    // The request fence prevents a peer registering mid-batch.
                    operations += IndexedDbWriteOperation.Delete(CommitJournalStoreName, journalKey)
                    operations += IndexedDbWriteOperation.Put("meta", CommitJournalFloorMetadataKey, journalKey)
                }
                byteStore.writeBatch(operations)
                lastJournalKey = journalKey
                observeCommittedVersion(persistedVersion)
                emitFlowUpdate(update)
                val journalEmpty = pruneWithCommit || collectCommittedJournalEntriesLocked()
                lastDrainedJournalClock = JournalClockSnapshot(clockBytes, journalEmpty, acknowledgedAt)
                byteStore.signalCommittedUpdate()
            } catch (error: Throwable) {
                lastDrainedJournalClock = null
                throw error
            }
        }
    }

    private suspend fun drainExternalCommits() = journalCursorMutex.withLock {
        lastDrainedJournalClock = null
        // Anchor before catch-up. If a fence is lost mid-drain, caching a later
        // clock could otherwise hide commits which this scan never observed.
        val clockBefore = byteStore.get("meta", CommitClockMetadataKey)
        drainExternalCommitsLocked()
        val acknowledgedAt = byteStore.currentEpochMillis()
        byteStore.writeBatch(listOf(journalConsumerOperation(lastJournalKey, acknowledgedAt)))
        val journalEmpty = collectCommittedJournalEntriesLocked()
        lastDrainedJournalClock = JournalClockSnapshot(clockBefore, journalEmpty, acknowledgedAt)
    }

    private suspend fun pollExternalCommits() = byteStore.transaction(
        setOf("meta", CommitJournalStoreName, CommitConsumerStoreName),
        IndexedDbTransactionMode.READWRITE,
    ) { drainExternalCommits() }

    private suspend fun drainExternalCommitsLocked() {
        if (!isSchemaCurrent()) {
            val failure = StaleIndexedDbSchemaException()
            failAllListeners(failure)
            throw failure
        }
        val floor = byteStore.get("meta", CommitJournalFloorMetadataKey)
        val cursor = lastJournalKey
        if (floor != null && cursor != null && floor.compareTo(cursor) > 0) {
            val latest = byteStore.scan(CommitJournalStoreName, reverse = true, limit = 1u).firstOrNull()?.first
            lastJournalKey = latest ?: floor
            val gap = StorageException(
                "IndexedDB update journal retention was exceeded; resubscribe to obtain a fresh snapshot"
            )
            failAllListeners(gap)
            throw gap
        }
        byteStore.scanInBatches(
            storeName = CommitJournalStoreName,
            startKey = cursor,
            includeStart = false,
            targetLimit = UInt.MAX_VALUE,
        ) { key, value ->
            val update = try {
                decodeCommitJournalEntry(indexedDbModelsById, sensitiveFields, key, value)
            } catch (exception: Throwable) {
                exception.rethrowIfFatal()
                lastJournalKey = key
                val error = exception as? StorageException ?: StorageException(
                    "IndexedDB update journal contains an invalid entry; resubscribe to obtain a fresh snapshot",
                    exception,
                )
                failAllListeners(error)
                throw error
            }
            lastJournalKey = key
            if (update == null) {
                observeCommittedVersion(value.readBigEndianULong(1 + UInt.SIZE_BYTES))
                return@scanInBatches true
            }
            observeCommittedVersion(update.version)
            emitFlowUpdate(update)
            true
        }
    }

    private fun journalConsumerOperation(
        cursor: ByteArray?,
        heartbeatMillis: ULong = byteStore.currentEpochMillis(),
    ) = IndexedDbWriteOperation.Put(
        CommitConsumerStoreName,
        journalConsumerKey,
        encodeJournalConsumer(heartbeatMillis, cursor),
    )

    private suspend fun validateJournalConsumersLocked(): Boolean {
        val consumers = byteStore.scan(CommitConsumerStoreName)
        for ((consumerKey, encoded) in consumers) decodeJournalConsumerOrThrow(consumerKey, encoded)
        return consumers.size == 1 && consumers.single().first.contentEquals(journalConsumerKey)
    }

    private fun decodeJournalConsumerOrThrow(consumerKey: ByteArray, encoded: ByteArray): JournalConsumer =
        try {
            decodeJournalConsumer(encoded)
        } catch (exception: Throwable) {
            exception.rethrowIfFatal()
            throw StorageException(
                "IndexedDB update journal contains invalid consumer metadata for ${consumerKey.size} byte key",
                exception,
            )
        }

    private suspend fun collectCommittedJournalEntriesLocked(): Boolean {
        val now = byteStore.currentEpochMillis()
        val consumers = byteStore.scan(CommitConsumerStoreName)
        val operations = mutableListOf<IndexedDbWriteOperation>()
        val activeCursors = mutableListOf<ByteArray>()
        for ((consumerKey, encoded) in consumers) {
            val consumer = decodeJournalConsumerOrThrow(consumerKey, encoded)
            if (now >= consumer.heartbeatMillis && now - consumer.heartbeatMillis > JournalConsumerTimeoutMillis) {
                operations += IndexedDbWriteOperation.Delete(CommitConsumerStoreName, consumerKey)
            } else {
                consumer.cursor?.let(activeCursors::add)
            }
        }

        val journalKeys = byteStore.getAllKeys(CommitJournalStoreName)
        var deleteThrough: ByteArray? = activeCursors.minWithOrNull { first, second -> first.compareTo(second) }
        if (journalKeys.size > indexedDbMaxRetainedJournalEntries) {
            val cappedFloor = journalKeys[journalKeys.size - indexedDbMaxRetainedJournalEntries - 1]
            if (deleteThrough == null || cappedFloor.compareTo(deleteThrough) > 0) deleteThrough = cappedFloor
        }
        val floor = deleteThrough
        var deletedJournalEntries = 0
        if (floor != null) {
            val deletions = journalKeys.takeWhile { key -> key.compareTo(floor) <= 0 }
            deletedJournalEntries = deletions.size
            for (key in deletions) operations += IndexedDbWriteOperation.Delete(CommitJournalStoreName, key)
            if (deletions.isNotEmpty()) {
                operations += IndexedDbWriteOperation.Put("meta", CommitJournalFloorMetadataKey, deletions.last())
            }
        }
        if (operations.isNotEmpty()) byteStore.writeBatch(operations)
        val journalEmpty = deletedJournalEntries == journalKeys.size
        mutationJournalScope?.let { scope ->
            scope.canPruneWithCommit = scope.soleConsumer && journalEmpty
        }
        return journalEmpty
    }

    /** Called under the writer fence before callbacks or listener registration can process updates. */
    private suspend fun initializeJournalCursor() {
        lastJournalKey = byteStore.scan(CommitJournalStoreName, reverse = true, limit = 1u).firstOrNull()?.first
            ?: byteStore.get("meta", CommitJournalFloorMetadataKey)
            ?: CommitJournalStartCursor
    }

    private suspend fun startExternalCommitListener() {
        byteStore.transaction(
            setOf("meta", CommitJournalStoreName, CommitConsumerStoreName),
            IndexedDbTransactionMode.READWRITE,
        ) {
            journalCursorMutex.withLock {
                byteStore.setExternalCommitListener {
                    if (externalCommitListenerReady) {
                        externalCommitPending = true
                        if (externalCommitDrainJob?.isActive != true) {
                            externalCommitDrainJob = launch {
                                while (externalCommitPending) {
                                    externalCommitPending = false
                                    try {
                                        pollExternalCommits()
                                    } catch (_: StaleIndexedDbSchemaException) {
                                        // Listeners were failed; this instance must be reopened.
                                        return@launch
                                    } catch (_: StorageException) {
                                        // A fresh subscription will establish a new snapshot boundary.
                                    }
                                }
                            }
                        }
                    } else {
                        externalCommitPending = true
                    }
                }
                initializeJournalCursor()
                byteStore.writeBatch(listOf(journalConsumerOperation(lastJournalKey)))
                externalCommitListenerReady = true
            }
        }
        if (externalCommitPending) {
            externalCommitPending = false
            pollExternalCommits()
        }
        journalPollingJob = launch {
            while (true) {
                delay(JournalPollIntervalMillis)
                if (!indexedDbJournalPollingPausedForTests) {
                    try {
                        pollExternalCommits()
                    } catch (_: StaleIndexedDbSchemaException) {
                        // Schema invalidation is terminal for this instance's listener stream.
                        return@launch
                    } catch (_: StorageException) {
                        // Active listeners were failed explicitly; a new subscription starts from a fresh snapshot.
                    }
                }
            }
        }
    }

    private suspend fun migrateHardDeleteTombstones() {
        if (!keepUpdateHistoryIndex) return
        for (modelId in dataModelsById.keys) {
            val markerKey = "hard-delete-history:$modelId".encodeToByteArray()
            if (byteStore.get("meta", markerKey) != null) continue

            byteStore.scanInBatches("uh:$modelId", targetLimit = UInt.MAX_VALUE) { rowKey, rowValue ->
                if (!rowKey.isUpdateHistoryRowKey(indexedDbModelsById.getValue(modelId).Meta.keyByteSize) ||
                    !rowValue.isHardDeleteTombstone()) return@scanInBatches true
                val version = rowKey.readInvertedVersion()
                val keyBytes = rowKey.copyOfRange(rowKey.size - indexedDbModelsById.getValue(modelId).Meta.keyByteSize, rowKey.size)
                hardDeleteTombstoneMigrationBeforeWrite?.invoke(modelId, keyBytes, version)
                byteStore.transaction(
                    setOf("hd:$modelId", "hdk:$modelId"),
                    IndexedDbTransactionMode.READWRITE,
                ) { store ->
                    val operations = mutableListOf<IndexedDbWriteOperation>()
                    operations += IndexedDbWriteOperation.Put("hdk:$modelId", createHardDeleteHistoryRowKey(keyBytes, version), byteArrayOf(1))
                    val current = store.get("hd:$modelId", keyBytes)?.readTrailingVersion()
                    if (current == null || version > current) {
                        operations += IndexedDbWriteOperation.Put("hd:$modelId", keyBytes, version.toBigEndianBytes())
                    }
                    store.writeBatch(operations)
                }
                true
            }
            byteStore.put("meta", markerKey, byteArrayOf(1))
        }
    }

    private suspend fun migrateSensitiveChangeLogs() {
        for ((modelId, dataModel) in indexedDbModelsById) {
            if (!sensitiveFields.hasSensitiveFields(modelId)) continue

            suspend fun migrateRows(
                storeName: String,
                identity: (ByteArray) -> Pair<ByteArray, ULong>?,
                isMarker: (ByteArray) -> Boolean,
            ) {
                val operations = mutableListOf<IndexedDbWriteOperation>()
                byteStore.scanInBatches(storeName, targetLimit = UInt.MAX_VALUE) { rowKey, rowValue ->
                    if (!isMarker(rowValue) && !sensitiveFields.isEncryptedChangeLogPayload(rowValue)) {
                        val (recordKey, version) = identity(rowKey) ?: return@scanInBatches true
                        operations += IndexedDbWriteOperation.Put(
                            storeName,
                            rowKey,
                            sensitiveFields.encryptChangeLogPayload(modelId, recordKey, version, rowValue),
                        )
                    }
                    if (operations.size >= SensitiveChangeLogMigrationBatchSize) {
                        byteStore.writeBatch(operations.toList())
                        operations.clear()
                    }
                    true
                }
                if (operations.isNotEmpty()) byteStore.writeBatch(operations)
            }

            val baseMarkerKey = sensitiveChangeLogMigrationMarker(modelId, "base")
            if (byteStore.get("meta", baseMarkerKey) == null) {
                migrateRows(
                    storeName = "c:$modelId",
                    identity = { rowKey -> objectKeyBytesFromScopedRowKey(rowKey) to rowKey.readTrailingVersion() },
                    isMarker = { it.isUnserializableChangeLogMarker() },
                )

                val journalOperations = mutableListOf<IndexedDbWriteOperation>()
                byteStore.scanInBatches(CommitJournalStoreName, targetLimit = UInt.MAX_VALUE) { rowKey, rowValue ->
                    migrateLegacyCommitJournalEntry(
                        indexedDbModelsById,
                        sensitiveFields,
                        modelId,
                        rowValue,
                    )?.let { migrated ->
                        journalOperations += IndexedDbWriteOperation.Put(CommitJournalStoreName, rowKey, migrated)
                    }
                    if (journalOperations.size >= SensitiveChangeLogMigrationBatchSize) {
                        byteStore.writeBatch(journalOperations.toList())
                        journalOperations.clear()
                    }
                    true
                }
                if (journalOperations.isNotEmpty()) byteStore.writeBatch(journalOperations)
                byteStore.put("meta", baseMarkerKey, byteArrayOf(1))
            }

            if (keepUpdateHistoryIndex) {
                val historyMarkerKey = sensitiveChangeLogMigrationMarker(modelId, "history")
                if (byteStore.get("meta", historyMarkerKey) == null) {
                    migrateRows(
                        storeName = "uh:$modelId",
                        identity = { rowKey ->
                            if (!rowKey.isUpdateHistoryRowKey(dataModel.Meta.keyByteSize)) null else {
                                rowKey.copyOfRange(rowKey.size - dataModel.Meta.keyByteSize, rowKey.size) to
                                    rowKey.readInvertedVersion()
                            }
                        },
                        isMarker = { it.isUnserializableChangeLogMarker() || it.isHardDeleteTombstone() },
                    )
                    byteStore.put("meta", historyMarkerKey, byteArrayOf(1))
                }
            }
        }
    }

    init {
        startFlows()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        launch {
            var clock = HLC()

            storeActorHasStarted.complete(Unit)
            try {
                processStoreActions(readStateAllocations = { model, key ->
                    val prefix = incrementingMapWatermarkPrefix(getDataModelId(model), key.bytes)
                    val collector = StateBackupAllocationCollector(model)
                    var cursor = prefix
                    var includeStart = true
                    while (true) {
                        val row = byteStore.scan("meta", startKey = cursor, includeStart = includeStart,
                            endKey = keyPrefixUpperBound(prefix), includeEnd = false, limit = 1u).singleOrNull() ?: break
                        collector.add(row.first.copyOfRange(prefix.size, row.first.size), row.second)
                        cursor = row.first
                        includeStart = false
                    }
                    collector.snapshot()
                }) { storeAction ->
                    try {
                        var needsFinalSchemaCheck = true
                        suspend fun process(
                            action: StoreAction<*, *, *> = storeAction,
                            prefetchedSnapshots: List<ByteArray?>? = null,
                            preparedSnapshots: MutableMap<Int, DecodedIndexedDbSnapshot>? = null,
                        ) {
                            @Suppress("UNCHECKED_CAST")
                            when (action.request) {
                                is AddRequest<*> -> processAddRequest(clock, action as AddStoreAction<IsRootDataModel>)
                                is ChangeRequest<*> -> processChangeRequest(clock, action as ChangeStoreAction<IsRootDataModel>)
                                is DeleteRequest<*> -> processDeleteRequest(clock, action as DeleteStoreAction<IsRootDataModel>)
                                is GetRequest<*> -> {
                                    needsFinalSchemaCheck = !processGetRequest(action as GetStoreAction<IsRootDataModel>, prefetchedSnapshots, preparedSnapshots)
                                }
                                is GetChangesRequest<*> -> processGetChangesRequest(action as GetChangesStoreAction<IsRootDataModel>)
                                is GetUpdatesRequest<*> -> processGetUpdatesRequest(action as GetUpdatesStoreAction<IsRootDataModel>)
                                is ScanRequest<*> -> processScanRequest(action as ScanStoreAction<IsRootDataModel>)
                                is ScanChangesRequest<*> -> processScanChangesRequest(action as ScanChangesStoreAction<IsRootDataModel>)
                                is ScanUpdateHistoryRequest<*>, is GetUpdateHistoryRequest<*> -> processScanUpdateHistoryRequest(action as ScanUpdateHistoryStoreAction<IsRootDataModel>)
                                is ScanUpdatesRequest<*> -> processScanUpdatesRequest(action as ScanUpdatesStoreAction<IsRootDataModel>)
                                is UpdateResponse<*> -> processUpdateResponse(action as ProcessUpdateResponseStoreAction<IsRootDataModel>)
                                else -> throw TypeException("Unknown request type ${action.request}")
                            }
                        }

                        if (storeAction.request.requestExecutionKind == RequestExecutionKind.Mutation) {
                            byteStore.transaction(
                                setOf("meta", CommitJournalStoreName),
                                IndexedDbTransactionMode.READWRITE,
                            ) {
                                if (!isSchemaCurrent()) throw RequestException(StaleSchemaMessage)
                                val persistedClockBytes = byteStore.get("meta", CommitClockMetadataKey)
                                val persistedClock = persistedClockBytes?.readBigEndianULong()
                                clock = nextMutationClock(
                                    clock,
                                    maxOf(
                                        persistedClock ?: 0uL,
                                        (storeAction.request as? UpdateResponse<*>)?.update?.version ?: 0uL,
                                    ),
                                )
                                mutationClock = clock
                                journalSequence = 0u
                                val knownJournal = lastDrainedJournalClock?.takeIf { it.bytes.contentEquals(persistedClockBytes) }
                                mutationJournalScope = MutationJournalScope(
                                    caughtUp = knownJournal != null,
                                    journalWasEmpty = knownJournal?.journalEmpty == true,
                                )
                                try {
                                    process()
                                } finally {
                                    mutationJournalScope = null
                                }
                            }
                        } else {
                            // Schema transitions can run in another browser context while this
                            // multi-read request suspends. Do not publish values decoded across it.
                            val getRequest = storeAction.request as? GetRequest<*>
                            // Read the revision and bounded current heads together. Validate
                            // before decoding; the final check still guards publication.
                            val projection = getRequest?.let { it.dataModel.requiredReadSelection(it.select, it.where, it.aggregations) }
                            val prepareProjection = getRequest != null && getRequest.keys.size in 2..256 && isNarrowSnapshotSelection(getRequest.dataModel, projection)
                            var preparedRows: MutableMap<Int, DecodedIndexedDbSnapshot>? = null
                            val guardedHeads = if (nativeByteStore is IndexedDbSchemaReadStore && getRequest != null &&
                                getRequest.toVersion == null && getRequest.keys.size in 1..256
                            ) {
                                val keys = getRequest.keys.map { it.bytes }
                                if (prepareProjection) {
                                    var knownSchema = false
                                    nativeByteStore.mapRecordsWithSchemaRevision("k:${getDataModelId(getRequest.dataModel)}", keys) { index, bytes ->
                                        if (index == 0) {
                                            knownSchema = bytes != null && bytes.contentEquals(schemaSnapshot?.validatedReadRevision)
                                            bytes
                                        } else if (knownSchema && bytes != null && bytes.size >= PREPARED_SNAPSHOT_MIN_BYTES) {
                                            val selected = prepareNarrowSnapshot(getRequest.dataModel, bytes, checkNotNull(projection))
                                            if (selected == null) bytes else {
                                                val rows = preparedRows ?: HashMap<Int, DecodedIndexedDbSnapshot>().also { preparedRows = it }
                                                rows[index - 1] = selected
                                                null
                                            }
                                        } else bytes
                                    }
                                } else nativeByteStore.getRecordsWithSchemaRevision("k:${getDataModelId(getRequest.dataModel)}", keys)
                            } else null
                            val schemaCurrent = if (guardedHeads != null) {
                                schemaSnapshot?.matches(nativeByteStore, guardedHeads[0]) != false
                            } else isSchemaCurrent()
                            if (!schemaCurrent) throw RequestException(StaleSchemaMessage)
                            val startingSchemaRevision = schemaSnapshot?.validatedReadRevision
                            val response = CompletableDeferred<IsResponse>()
                            @Suppress("UNCHECKED_CAST")
                            val readAction = StoreAction(
                                storeAction.request as IsStoreRequest<IsRootDataModel, IsResponse>, response,
                                storeAction.onBeforeReadContext, storeAction.onFlowSnapshotBoundary,
                                storeAction.executionContext,
                            )
                            process(readAction, guardedHeads?.subList(1, guardedHeads.size), preparedRows)
                            val result = response.await()
                            // Complete heads and their schema came from one native snapshot.
                            // Legacy table fallbacks and other multi-read paths still need a
                            // final guard because their bytes can span schema generations.
                            if (needsFinalSchemaCheck &&
                                schemaSnapshot?.matchesReadRevision(nativeByteStore, startingSchemaRevision) == false
                            ) throw RequestException(StaleSchemaMessage)
                            @Suppress("UNCHECKED_CAST")
                            (storeAction.response as CompletableDeferred<IsResponse>).complete(result)
                        }
                    } catch (e: CancellationException) {
                        if (currentCoroutineContext().isActive) {
                            storeAction.response.completeExceptionally(e)
                        } else {
                            storeAction.response.cancel(e)
                            throw e
                        }
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

    internal suspend fun backfillIndexRows(
        dataModel: IsRootDataModel,
        indexesToIndex: List<IsIndexable>,
    ) {
        if (indexesToIndex.isEmpty()) return

        val modelId = getDataModelId(dataModel)
        val indexStoreName = "i:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val historicIndexCleanupStoreName = "hik:$modelId"
        val indexReferences = indexesToIndex.map { it.referenceStorageByteArray.bytes }
        val indexPrefixes = indexReferences.map(::createIndexKeyPrefix)
        val operations = mutableListOf<IndexedDbWriteOperation>()

        fun CurrentStateStoragePlan.filteredIndexRows(): List<ByteArray> =
            indexRows.filter { rowKey ->
                indexPrefixes.any { prefix ->
                    rowKey.matchesRangePart(0, prefix, sourceLength = rowKey.size, length = prefix.size)
                }
            }

        byteStore.scanInBatches(storeName = "k:$modelId", targetLimit = UInt.MAX_VALUE) { keyBytes, snapshot ->
            val current = decodeCurrentSnapshotRecord(
                dataModel = dataModel,
                keyBytes = keyBytes,
                snapshot = snapshot,
                select = null,
                decryptValue = { qualifier, value -> sensitiveFields.decryptValueIfNeeded(modelId, keyBytes, qualifier, value) },
            ) ?: return@scanInBatches true
            if (current.isDeleted) return@scanInBatches true

            val plan = createStoragePlan(dataModel, modelId, keyBytes, current.values, sensitiveFields)
            for (indexRow in plan.filteredIndexRows()) {
                operations.put(indexStoreName, indexRow, keyBytes)
            }
            if (operations.size >= 500) {
                byteStore.writeBatch(operations.toList())
                operations.clear()
            }
            true
        }

        if (keepAllVersions) {
            var currentHistoricKey: ByteArray? = null
            val snapshotsForKey = mutableListOf<Pair<ByteArray, ByteArray>>()

            suspend fun replayHistoricSnapshotsForKey(snapshots: List<Pair<ByteArray, ByteArray>>) {
                var previousRowsByKey = emptyMap<Bytes, ByteArray>()
                val chronologicalSnapshots = snapshots.sortedWith { first, second ->
                    first.first.readTrailingInvertedVersion().compareTo(second.first.readTrailingInvertedVersion())
                }

                for ((rowKey, snapshot) in chronologicalSnapshots) {
                    val keyBytes = objectKeyBytesFromScopedRowKey(rowKey)
                    val version = rowKey.readTrailingInvertedVersion()
                    val currentRowsByKey = mutableMapOf<Bytes, ByteArray>()
                    val (meta, storageRows) = decodeHistoricSnapshot(snapshot)
                    if (!meta.isDeleted) {
                        val values = decodeStorageRowsToValues(
                            dataModel = dataModel,
                            rows = storageRows.map { (qualifier, value) ->
                                qualifier to sensitiveFields.decryptValueIfNeeded(modelId, keyBytes, qualifier, value)
                            },
                            select = null,
                        )
                        if (values != null) {
                            val plan = createStoragePlan(dataModel, modelId, keyBytes, values, sensitiveFields)
                            for (indexRow in plan.filteredIndexRows()) {
                                currentRowsByKey[Bytes(indexRow)] = indexRow
                            }
                        }
                    }

                    val removedRows = previousRowsByKey
                        .filterKeys { it !in currentRowsByKey }
                        .values
                        .toList()
                    if (removedRows.isNotEmpty()) {
                        operations.addHistoricIndexRows(
                            historicIndexStoreName,
                            historicIndexCleanupStoreName,
                            keyBytes,
                            removedRows,
                            version,
                            active = false,
                        )
                    }
                    if (currentRowsByKey.isNotEmpty()) {
                        operations.addHistoricIndexRows(
                            historicIndexStoreName,
                            historicIndexCleanupStoreName,
                            keyBytes,
                            currentRowsByKey.values.toList(),
                            version,
                            active = true,
                        )
                    }
                    previousRowsByKey = currentRowsByKey

                    if (operations.size >= 500) {
                        byteStore.writeBatch(operations.toList())
                        operations.clear()
                    }
                }
            }

            byteStore.scanInBatches(storeName = "ht:$modelId", targetLimit = UInt.MAX_VALUE) { rowKey, snapshot ->
                val keyBytes = objectKeyBytesFromScopedRowKey(rowKey)
                val activeKey = currentHistoricKey
                if (activeKey != null && !activeKey.contentEquals(keyBytes)) {
                    replayHistoricSnapshotsForKey(snapshotsForKey)
                    snapshotsForKey.clear()
                }
                currentHistoricKey = keyBytes
                snapshotsForKey += rowKey to snapshot
                true
            }

            if (snapshotsForKey.isNotEmpty()) {
                replayHistoricSnapshotsForKey(snapshotsForKey)
            }
        }

        if (operations.isNotEmpty()) {
            byteStore.writeBatch(operations)
        }
    }

    override suspend fun compactReplicationTombstones(upToVersionInclusive: ULong) {
        byteStore.transaction(setOf("meta"), IndexedDbTransactionMode.READWRITE) {
            if (!isSchemaCurrent()) throw RequestException(StaleSchemaMessage)
        }
        for (modelId in dataModelsById.keys) {
            val storeName = "hd:$modelId"
            var nextStart: ByteArray? = null
            while (true) {
                val rows = byteStore.scan(
                    storeName = storeName,
                    startKey = nextStart,
                    includeStart = false,
                    limit = 256u,
                )
                if (rows.isEmpty()) break
                replicationTombstoneCompactionAfterScan?.invoke(modelId)
                byteStore.transaction(setOf("meta", storeName), IndexedDbTransactionMode.READWRITE) { store ->
                    if (!isSchemaCurrent()) throw RequestException(StaleSchemaMessage)
                    val operations = mutableListOf<IndexedDbWriteOperation>()
                    for ((key, _) in rows) {
                        val currentValue = store.get(storeName, key) ?: continue
                        if (currentValue.readTrailingVersion() <= upToVersionInclusive) {
                            operations += IndexedDbWriteOperation.Delete(storeName, key)
                        }
                    }
                    if (operations.isNotEmpty()) store.writeBatch(operations)
                }
                nextStart = rows.last().first
            }
        }
    }

    override suspend fun close() {
        withContext(NonCancellable) {
            if (!startClosingDataStore()) return@withContext
            try {
                byteStore.setExternalCommitListener(null)
                journalPollingJob?.cancelAndJoin()
                externalCommitDrainJob?.cancelAndJoin()
            } finally {
                try {
                    cancelAndJoinDataStoreScope()
                } finally {
                    try {
                        byteStore.transaction(setOf(CommitConsumerStoreName), IndexedDbTransactionMode.READWRITE) {
                            byteStore.delete(CommitConsumerStoreName, journalConsumerKey)
                        }
                    } finally {
                        byteStore.close()
                    }
                }
            }
        }
    }

    private suspend fun closeInvalidatedByteStore() {
        withContext(NonCancellable) {
            if (!startClosingDataStore()) return@withContext
            byteStore.setExternalCommitListener(null)
            journalPollingJob?.cancelAndJoin()
            externalCommitDrainJob?.cancelAndJoin()
            cancelAndJoinDataStoreScope()
        }
    }

    private suspend fun stopFailedStartupWork() {
        if (!startClosingDataStore()) return
        cancelAndJoinDataStoreScope()
    }

    companion object {
        suspend fun open(
            databaseName: String,
            dataModelsById: Map<UInt, IsRootDataModel>,
            keepAllVersions: Boolean = false,
            keepUpdateHistoryIndex: Boolean = false,
            fieldEncryptionProvider: FieldEncryptionProvider? = null,
            migrationConfiguration: MigrationConfiguration<IndexedDbDataStore> = MigrationConfiguration(),
            versionUpdateHandler: VersionUpdateHandler<IndexedDbDataStore>? = null,
        ): IndexedDbDataStore {
            val sensitiveFields = IndexedDbSensitiveFieldSupport(dataModelsById, fieldEncryptionProvider)
            val objectStoreNames = buildSet {
                add("meta")
                add(CommitJournalStoreName)
                add(CommitConsumerStoreName)
                for (modelId in dataModelsById.keys) {
                    add("k:$modelId")
                    add("t:$modelId")
                    add("i:$modelId")
                    add("u:$modelId")
                    add("c:$modelId")
                    add("hd:$modelId")
                    if (keepAllVersions) {
                        add("ht:$modelId")
                        add("hi:$modelId")
                        add("hu:$modelId")
                        add("hik:$modelId")
                        add("huk:$modelId")
                    }
                    if (keepUpdateHistoryIndex) {
                        add("uh:$modelId")
                        add("hdk:$modelId")
                    }
                }
            }

            val byteStore = openIndexedDbByteStore(
                databaseName = databaseName,
                objectStoreNames = objectStoreNames,
            )

            val dataStore = IndexedDbDataStore(
                byteStore = byteStore,
                keepAllVersions = keepAllVersions,
                keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                dataModelsById = dataModelsById,
                sensitiveFields = sensitiveFields,
            )

            return try {
                byteStore.withStartupWriteLock {
                    try {
                        byteStore.upgradeIndexedDbStorageFormat(
                            keepAllVersions,
                            keepUpdateHistoryIndex,
                            fieldEncryptionProvider,
                            preparePublication = {
                                dataStore.migrateHardDeleteTombstones()
                                dataStore.migrateSensitiveChangeLogs()
                            },
                        )
                        dataStore.initializeJournalCursor()
                        byteStore.migrateStoreMetadata(
                            dataStore = dataStore,
                            keepAllVersions = keepAllVersions,
                            keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                            dataModelsById = dataModelsById,
                            migrationConfiguration = migrationConfiguration,
                            versionUpdateHandler = versionUpdateHandler,
                        )
                        dataStore.schemaSnapshot = byteStore.captureSchemaSnapshot(dataModelsById.keys)
                    } catch (error: Throwable) {
                        withContext(NonCancellable) {
                            dataStore.stopFailedStartupWork()
                        }
                        throw error
                    }
                }
                dataStore.startExternalCommitListener()
                dataStore
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    dataStore.stopFailedStartupWork()
                    byteStore.close()
                }
                throw error
            }
        }
    }
}

private const val SensitiveChangeLogMigrationBatchSize = 256
private const val StaleSchemaMessage = "IndexedDB schema changed or a migration is pending; reopen this datastore with the current models before writing"

private class StaleIndexedDbSchemaException : RequestException(StaleSchemaMessage)
private val SensitiveChangeLogMigrationMetadataPrefix = "sensitive-change-log-v1:".encodeToByteArray()

private fun sensitiveChangeLogMigrationMarker(modelId: UInt, storeGroup: String): ByteArray =
    SensitiveChangeLogMigrationMetadataPrefix + "$modelId:$storeGroup".encodeToByteArray()
