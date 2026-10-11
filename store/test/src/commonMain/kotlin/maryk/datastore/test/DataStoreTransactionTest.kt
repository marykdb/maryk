package maryk.datastore.test

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.TransactionCancellationException
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.transaction
import maryk.datastore.shared.transactionCapabilities
import maryk.datastore.shared.transactionWithRetry
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel



private suspend fun readsOwnWritesAndRepeatedChangesCommitOnce(store: IsDataStore) {
    val ref = SimpleMarykModel.ref { value }
    val baseline = store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size
    val values = SimpleMarykModel.create { value with "haha-initial" }
    val key = SimpleMarykModel.key(values)
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(key to values))
    assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
    val staged = tx.execute(SimpleMarykModel.get(key)).response.values.single()
    assertEquals("haha-initial", staged.values[ref])
    tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-first"), lastVersion = staged.lastVersion)))
    val changed = tx.execute(SimpleMarykModel.get(key)).response.values.single()
    tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-final"), lastVersion = changed.lastVersion)))
    assertEquals("haha-final", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[ref])
    val commit = tx.commit()
    assertEquals(TransactionStatus.Committed, commit.status)
    assertEquals("haha-final", store.execute(SimpleMarykModel.get(key)).values.single().values[ref])
    assertEquals(commit, tx.commit())
}

private suspend fun savepointRestoresWritesAndCanBeUsedAgain(store: IsDataStore) {
    val ref = SimpleMarykModel.ref { value }
    val baseline = store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size
    val values = SimpleMarykModel.create { value with "haha-base" }
    val key = SimpleMarykModel.key(values)
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(key to values))
    val point = tx.savepoint()
    tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-discard"))))
    assertEquals("haha-discard", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[ref])
    tx.rollbackTo(point)
    assertEquals("haha-base", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[ref])
    tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-keep"))))
    tx.rollbackTo(point)
    tx.release(point)
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-base", store.execute(SimpleMarykModel.get(key)).values.single().values[ref])
}

private suspend fun rollbackAndBlockFailurePublishNothing(store: IsDataStore) {
    val baseline = store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-rollback" }))
    tx.rollback()
    assertFailsWith<TransactionException> { tx.commit() }
    assertFailsWith<IllegalStateException> {
        store.transaction { scoped ->
            scoped.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-failure" }))
            error("Stop before commit")
        }
    }
    assertEquals(baseline, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
}

private suspend fun concurrentInsertionConflictsWithAnEmptyScan(store: IsDataStore) {
    val baseline = store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size
    val tx = store.beginTransaction()
    assertEquals(baseline, tx.execute(SimpleMarykModel.scan(allowTableScan = true)).response.values.size)
    store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-phantom" }))
    val commit = tx.commit()
    assertEquals(TransactionStatus.Aborted, commit.status)
    assertEquals(AtomicFailureCode.Conflict, commit.failure?.code)
    assertEquals(baseline + 1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
}

private suspend fun duplicateAfterEarlierWriteAbortsTheCompleteTransaction(store: IsDataStore) {
    val baseline = store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size
    val values = SimpleMarykModel.create { value with "haha-duplicate" }
    val key = SimpleMarykModel.key(values)
    store.execute(SimpleMarykModel.add(key to values))
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-new" }))
    assertFailsWith<TransactionException> { tx.execute(SimpleMarykModel.add(key to values)) }
    assertEquals(baseline + 1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
}

private suspend fun hardDeleteAndReinsertAreVisibleWithinTheTransaction(store: IsDataStore) {
    val ref = SimpleMarykModel.ref { value }
    val baseline = store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size
    val values = SimpleMarykModel.create { value with "haha-existing" }
    val key = SimpleMarykModel.key(values)
    store.execute(SimpleMarykModel.add(key to values))
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.delete(key, hardDelete = true))
    assertTrue(tx.execute(SimpleMarykModel.get(key)).response.values.isEmpty())
    tx.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "haha-replaced" }))
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-replaced", store.execute(SimpleMarykModel.get(key)).values.single().values[ref])
}

/** Named cases run against every built-in backend, with fixture cleanup between cases. */
suspend fun runTransactionDataStoreTests(store: IsDataStore) {
    val cases = mapOf<String, suspend (IsDataStore) -> Unit>(
        "readOwnWritesAndRepeatedVersions" to ::readsOwnWritesAndRepeatedChangesCommitOnce,
        "savepointReuse" to ::savepointRestoresWritesAndCanBeUsedAgain,
        "rollbackAndCallbackFailure" to ::rollbackAndBlockFailurePublishNothing,
        "phantomConflict" to ::concurrentInsertionConflictsWithAnEmptyScan,
        "lateDuplicateRollback" to ::duplicateAfterEarlierWriteAbortsTheCompleteTransaction,
        "hardDeleteAndReinsert" to ::hardDeleteAndReinsertAreVisibleWithinTheTransaction,
        "readOnlyCommits" to ::readOnlyCommitsDoNotConflictWithEachOther,
        "indexedWrites" to ::repeatedIndexedWritesRemoveIntermediateEntries,
        "softDeleteAndRestore" to ::transactionSoftDeleteAndRestore,
        "crossModelBatchAndChecks" to ::transactionCrossModelBatchAndChecks,
        "atomicChecksInsideScope" to ::transactionAtomicChecksInsideScope,
        "savepointAfterValidationFailure" to ::transactionSavepointAfterValidationFailure,
        "readonlyAndUnsupportedActions" to ::transactionReadOnlyAndUnsupportedActions,
        "ambientRollbackAndFlowRejection" to ::transactionAmbientRollbackAndFlowRejection,
        "lostCommitAndReceiptMismatch" to ::transactionLostCommitAndReceiptMismatch,
        "cancellationBeforeAndAfterCommit" to ::transactionCancellationBeforeAndAfterCommit,
        "operationLimitAndSavepointOwnership" to ::transactionOperationLimitAndSavepointOwnership,
        "callerOwnedChangesAreFrozen" to ::transactionCallerOwnedChangesAreFrozen,
        "historyVersionsAndListenerPublication" to ::transactionHistoryVersionsAndListenerPublication,
        "replicationConflicts" to ::transactionReplicationConflicts,
        "explicitConflictRetry" to ::transactionExplicitConflictRetry,
        "deadlineDiscardsStagedWrites" to ::transactionDeadlineDiscardsStagedWrites,
        "uniqueOwnershipAndRecovery" to ::transactionUniqueOwnershipAndRecovery,
        "collectionDeltasReplayOnce" to ::transactionCollectionDeltasReplayOnce,
        "generatedValuesSurviveCommitRecovery" to ::transactionGeneratedValuesSurviveCommitRecovery,
        "duplicateTargetsWithinOneRequest" to ::transactionDuplicateTargetsWithinOneRequest,
        "replicationOnlyChangesRejected" to ::transactionCreationThroughChange,
    )
    for ((name, case) in cases) {
        val simpleBefore = store.execute(SimpleMarykModel.scan(limit = 100_000u, allowTableScan = true)).values.map { it.key }.toSet()
        val personBefore = store.execute(Person.scan(limit = 100_000u, allowTableScan = true)).values.map { it.key }.toSet()
        try { case(store) }
        catch (error: Throwable) { throw AssertionError("Transaction contract '$name' failed on ${store::class.simpleName}", error) }
        finally {
            val simpleAdded = store.execute(SimpleMarykModel.scan(limit = 100_000u, filterSoftDeleted = false, allowTableScan = true)).values.map { it.key }.filter { it !in simpleBefore }
            val personAdded = store.execute(Person.scan(limit = 100_000u, filterSoftDeleted = false, allowTableScan = true)).values.map { it.key }.filter { it !in personBefore }
            if (simpleAdded.isNotEmpty()) store.execute(SimpleMarykModel.delete(*simpleAdded.toTypedArray(), hardDelete = true))
            if (personAdded.isNotEmpty()) store.execute(Person.delete(*personAdded.toTypedArray(), hardDelete = true))
        }
    }
}

suspend fun testTransactionCompetingStores(first: IsDataStore, second: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-shared" }
    val key = SimpleMarykModel.key(values)
    first.execute(SimpleMarykModel.add(key to values))
    val a = first.beginTransaction()
    val b = second.beginTransaction()
    a.execute(SimpleMarykModel.get(key))
    b.execute(SimpleMarykModel.get(key))
    a.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-winner"))))
    b.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-conflict"))))
    assertEquals(TransactionStatus.Committed, a.commit().status)
    val rejected = b.commit()
    assertEquals(TransactionStatus.Aborted, rejected.status)
    assertEquals(AtomicFailureCode.Conflict, rejected.failure?.code)
    assertEquals("haha-winner", first.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
}

suspend fun repeatedIndexedWritesRemoveIntermediateEntries(store: IsDataStore) {
    val suffix = Random.nextInt().toString()
    val a = "A$suffix"
    val b = "B$suffix"
    val c = "C$suffix"
    val values = Person.create { firstName with "Transaction"; surname with a }
    val key = Person.key(values)
    val tx = store.beginTransaction()
    tx.execute(Person.add(key to values))
    tx.execute(Person.change(key.change(Change(Person.ref { surname } with b))))
    tx.execute(Person.change(key.change(Change(Person.ref { surname } with c))))
    assertTrue(tx.execute(Person.scan(where = Equals(Person.ref { surname } with a), allowTableScan = true)).response.values.isEmpty())
    assertTrue(tx.execute(Person.scan(where = Equals(Person.ref { surname } with b), allowTableScan = true)).response.values.isEmpty())
    assertEquals(1, tx.execute(Person.scan(where = Equals(Person.ref { surname } with c), allowTableScan = true)).response.values.size)
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertTrue(store.execute(Person.scan(where = Equals(Person.ref { surname } with b), allowTableScan = true)).values.isEmpty())
    assertEquals(1, store.execute(Person.scan(where = Equals(Person.ref { surname } with c), allowTableScan = true)).values.size)
}

suspend fun readOnlyCommitsDoNotConflictWithEachOther(store: IsDataStore) {
    val a = store.beginTransaction(readOnly = true)
    val b = store.beginTransaction(readOnly = true)
    a.execute(SimpleMarykModel.scan(allowTableScan = true))
    b.execute(SimpleMarykModel.scan(allowTableScan = true))
    assertEquals(TransactionStatus.Committed, a.commit().status)
    assertEquals(TransactionStatus.Committed, b.commit().status)
}

private suspend fun transactionSoftDeleteAndRestore(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-soft" }
    val key = SimpleMarykModel.key(values)
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(key to values))
    tx.execute(SimpleMarykModel.delete(key))
    assertTrue(tx.execute(SimpleMarykModel.get(key)).response.values.isEmpty())
    val deleted = tx.execute(SimpleMarykModel.get(key, filterSoftDeleted = false)).response.values.single()
    assertTrue(deleted.isDeleted)
    tx.execute(SimpleMarykModel.change(key.change(ObjectSoftDeleteChange(false), lastVersion = deleted.lastVersion)))
    assertEquals(false, tx.execute(SimpleMarykModel.get(key)).response.values.single().isDeleted)
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals(false, store.execute(SimpleMarykModel.get(key)).values.single().isDeleted)
}

private suspend fun transactionCrossModelBatchAndChecks(store: IsDataStore) {
    val simple = SimpleMarykModel.create { value with "haha-batch" }
    val person = Person.create { firstName with "Transaction"; surname with "Batch" }
    val a = SimpleMarykModel.key(simple)
    val b = Person.key(person)
    val tx = store.beginTransaction()
    assertEquals(2, tx.executeBatch(listOf(SimpleMarykModel.add(a to simple), Person.add(b to person))).responses.size)
    tx.execute(SimpleMarykModel.change(a.change(Check(SimpleMarykModel.ref { value } with "haha-batch"))))
    tx.execute(SimpleMarykModel.change(a.change(Change(SimpleMarykModel.ref { value } with "haha-checked"))))
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-checked", store.execute(SimpleMarykModel.get(a)).values.single().values[SimpleMarykModel.ref { value }])
    assertEquals("Batch", store.execute(Person.get(b)).values.single().values[Person.ref { surname }])
}

private suspend fun transactionAtomicChecksInsideScope(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-initial-check" }
    val key = SimpleMarykModel.key(values)
    val result = store.transaction { tx ->
        tx.execute(SimpleMarykModel.add(key to values))
        val atomic = AtomicRequest("inner-check", SimpleMarykModel.change(key.change(
            Change(SimpleMarykModel.ref { value } with "haha-changed-check"),
            Check(SimpleMarykModel.ref { value } with "haha-initial-check"),
        )))
        assertEquals(AtomicStatus.Staged, store.executeAtomic(atomic).status)
        assertEquals("haha-changed-check", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[SimpleMarykModel.ref { value }])
    }
    assertEquals(TransactionStatus.Committed, result.commit.status)
}

private suspend fun transactionSavepointAfterValidationFailure(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-recovery" }
    val key = SimpleMarykModel.key(values)
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(key to values))
    val point = tx.savepoint()
    val failure = assertFailsWith<TransactionException> {
        tx.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "invalid"))))
    }
    assertEquals(AtomicFailureCode.ValidationFailed, failure.response.failure?.code)
    tx.rollbackTo(point)
    tx.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-recovered"))))
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-recovered", store.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
}

private suspend fun transactionReadOnlyAndUnsupportedActions(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-readonly-check" }
    val key = SimpleMarykModel.key(values)
    val original = assertIs<AddSuccess<*>>(store.execute(SimpleMarykModel.add(key to values)).statuses.single()).version
    val tx = store.beginTransaction(readOnly = true)
    tx.execute(SimpleMarykModel.change(key.change(Check(SimpleMarykModel.ref { value } with "haha-readonly-check"), lastVersion = original)))
    assertFailsWith<RequestException> { tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-denied" })) }
    assertFailsWith<RequestException> { tx.execute(SimpleMarykModel.getChanges()) }
    tx.execute(SimpleMarykModel.scan(allowTableScan = true))
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertFailsWith<TransactionException> { tx.execute(SimpleMarykModel.scan(allowTableScan = true)) }
    assertFailsWith<IllegalStateException> { tx.rollback() }
    assertEquals(original, store.execute(SimpleMarykModel.get(key)).values.single().lastVersion)
}

private suspend fun transactionAmbientRollbackAndFlowRejection(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-ambient-rollback" }
    val key = SimpleMarykModel.key(values)
    assertFailsWith<IllegalStateException> {
        store.transaction {
            store.execute(SimpleMarykModel.add(key to values))
            assertEquals(1, store.execute(SimpleMarykModel.get(key)).values.size)
            assertFailsWith<IllegalStateException> { store.beginTransaction() }
            assertFailsWith<IllegalStateException> { store.executeFlow(SimpleMarykModel.get(key)) }
            error("Rollback callback")
        }
    }
    assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
}

private suspend fun transactionLostCommitAndReceiptMismatch(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-receipt" }
    val key = SimpleMarykModel.key(values)
    var original: TransactionRequest? = null
    var lose = true
    val transport = object : IsDataStore by store, TransactionalDataStore {
        override val transactionCapabilities get() = store.transactionCapabilities
        override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
            val response = store.executeTransaction(request)
            if (request.commit && lose) { lose = false; original = request; error("Lost response") }
            return response
        }
    }
    val tx = transport.beginTransaction()
    tx.execute(SimpleMarykModel.add(key to values))
    assertEquals(TransactionStatus.OutcomeUnknown, tx.commit().status)
    assertFailsWith<IllegalStateException> { tx.rollback() }
    // A later business write must not hide the successful receipt behind a stale revision.
    store.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-after-receipt"))))
    val committed = tx.resolveCommit()
    assertEquals(TransactionStatus.Committed, committed.status)
    assertEquals(committed, store.executeTransaction(requireNotNull(original)))
    val mismatch = store.executeTransaction(TransactionRequest(tx.id, commit = true))
    assertEquals(TransactionStatus.Aborted, mismatch.status)
    assertEquals(AtomicFailureCode.ChangeSetMismatch, mismatch.failure?.code)
    assertEquals("haha-after-receipt", store.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
}

private suspend fun transactionCancellationBeforeAndAfterCommit(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-canceled" }
    val key = SimpleMarykModel.key(values)
    assertFailsWith<CancellationException> {
        store.transaction { tx -> tx.execute(SimpleMarykModel.add(key to values)); throw CancellationException("Before commit") }
    }
    assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
    var lose = true
    val transport = object : IsDataStore by store, TransactionalDataStore {
        override val transactionCapabilities get() = store.transactionCapabilities
        override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
            val response = store.executeTransaction(request)
            if (request.commit && lose) { lose = false; throw CancellationException("After commit") }
            return response
        }
    }
    val failure = assertFailsWith<TransactionCancellationException> {
        transport.transaction { tx -> tx.execute(SimpleMarykModel.add(key to values)) }
    }
    assertEquals(TransactionStatus.Committed, failure.transaction.resolveCommit().status)
    assertEquals(1, store.execute(SimpleMarykModel.get(key)).values.size)
}

private suspend fun transactionOperationLimitAndSavepointOwnership(store: IsDataStore) {
    val bounded = object : IsDataStore by store, TransactionalDataStore {
        override val transactionCapabilities = TransactionCapabilities(requireNotNull(store.transactionCapabilities).storage, maxOperations = 2u, maxSavepoints = 1u)
        override suspend fun executeTransaction(request: TransactionRequest) = store.executeTransaction(request)
    }
    val tx = bounded.beginTransaction()
    val point = tx.savepoint()
    assertEquals(AtomicFailureCode.LimitExceeded, assertFailsWith<TransactionException> { tx.savepoint() }.response.failure?.code)
    tx.execute(SimpleMarykModel.scan(allowTableScan = true))
    tx.execute(SimpleMarykModel.scan(allowTableScan = true))
    assertEquals(AtomicFailureCode.LimitExceeded, assertFailsWith<TransactionException> { tx.execute(SimpleMarykModel.scan(allowTableScan = true)) }.response.failure?.code)
    tx.rollbackTo(point)
    val other = store.beginTransaction()
    assertFailsWith<IllegalArgumentException> { other.rollbackTo(point) }
    other.rollback()
    tx.release(point)
    assertFailsWith<IllegalArgumentException> { tx.rollbackTo(point) }
    assertEquals(TransactionStatus.Committed, tx.commit().status)
}

private suspend fun transactionCallerOwnedChangesAreFrozen(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-owned" }
    val key = SimpleMarykModel.key(values)
    store.execute(SimpleMarykModel.add(key to values))
    val changes = Change(SimpleMarykModel.ref { value } with "haha-submitted")
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.change(key.change(changes)))
    (changes.referenceValuePairs as MutableList).clear()
    assertEquals("haha-submitted", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[SimpleMarykModel.ref { value }])
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-submitted", store.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])

}

private suspend fun transactionHistoryVersionsAndListenerPublication(store: IsDataStore) = coroutineScope {
    val values = SimpleMarykModel.create { value with "haha-history-original" }
    val key = SimpleMarykModel.key(values)
    val initial = assertIs<AddSuccess<*>>(store.execute(SimpleMarykModel.add(key to values)).statuses.single()).version
    val events = Channel<IsUpdateResponse<SimpleMarykModel>>(Channel.UNLIMITED)
    val listener = launch { store.executeFlow(SimpleMarykModel.get(key)).collect { events.send(it) } }
    try {
        events.receive()
        val tx = store.beginTransaction()
        tx.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-history-first"))))
        tx.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-history-final"))))
        assertTrue(events.tryReceive().isFailure, "Previews must not publish listener events")
        assertEquals(TransactionStatus.Committed, tx.commit().status)
        val first = events.receive().version
        val second = events.receive().version
        assertTrue(initial < first && first < second, "Repeated writes must have distinct ordered durable versions")
        if (store.keepAllVersions) {
            assertEquals("haha-history-first", store.execute(SimpleMarykModel.get(key, toVersion = first)).values.single().values[SimpleMarykModel.ref { value }])
            assertEquals("haha-history-final", store.execute(SimpleMarykModel.get(key, toVersion = second)).values.single().values[SimpleMarykModel.ref { value }])
            assertEquals(3, store.execute(SimpleMarykModel.getChanges(key, maxVersions = 100u)).changes.single().changes.size)
        }
    } finally { listener.cancelAndJoin(); events.cancel() }
}

private suspend fun transactionReplicationConflicts(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-replicated" }
    val key = SimpleMarykModel.key(values)
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.scan(allowTableScan = true))
    val version = HLC().timestamp
    store.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(key, version, version, 0, false, values)))
    val result = tx.commit()
    assertEquals(TransactionStatus.Aborted, result.status)
    assertEquals(AtomicFailureCode.Conflict, result.failure?.code)
}

private suspend fun transactionExplicitConflictRetry(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-retry" }
    val key = SimpleMarykModel.key(values)
    var attempts = 0
    val result = store.transactionWithRetry { tx ->
        attempts++
        tx.execute(SimpleMarykModel.scan(allowTableScan = true))
        if (attempts == 1) {
            // A separate low-level request simulates an independent caller.
            assertEquals(TransactionStatus.Committed, store.executeTransaction(TransactionRequest("retry-$key", listOf(SimpleMarykModel.add(key to values)), commit = true)).status)
        }
        attempts
    }
    assertEquals(2, attempts)
    assertEquals(2, result.value)
    assertEquals(TransactionStatus.Committed, result.commit.status)
}

private suspend fun transactionDeadlineDiscardsStagedWrites(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-deadline" }
    val key = SimpleMarykModel.key(values)
    val bounded = object : IsDataStore by store, TransactionalDataStore {
        override val transactionCapabilities = TransactionCapabilities(requireNotNull(store.transactionCapabilities).storage, maxLifetimeMillis = 500u)
        override suspend fun executeTransaction(request: TransactionRequest) = store.executeTransaction(request)
    }
    val result = bounded.transaction { tx -> tx.execute(SimpleMarykModel.add(key to values)); delay(1000) }
    assertEquals(TransactionStatus.Aborted, result.commit.status)
    assertEquals(AtomicFailureCode.Expired, result.commit.failure?.code)
    assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
}

private suspend fun transactionUniqueOwnershipAndRecovery(store: IsDataStore) {
    val suffix = Random.nextInt().toString()
    val a = UniqueModel.create { email with "a-$suffix@transaction.test" }
    val b = UniqueModel.create { email with "b-$suffix@transaction.test" }
    val ka = UniqueModel.key(a)
    val kb = UniqueModel.key(b)
    val tx = store.beginTransaction()
    try {
        tx.executeBatch(listOf(UniqueModel.add(ka to a), UniqueModel.add(kb to b)))
        tx.execute(UniqueModel.change(ka.change(Change(UniqueModel.ref { email } with "c-$suffix@transaction.test"))))
        tx.execute(UniqueModel.change(kb.change(Change(UniqueModel.ref { email } with "a-$suffix@transaction.test"))))
        val point = tx.savepoint()
        assertFailsWith<TransactionException> { tx.execute(UniqueModel.change(ka.change(Change(UniqueModel.ref { email } with "a-$suffix@transaction.test")))) }
        tx.rollbackTo(point)
        assertEquals(TransactionStatus.Committed, tx.commit().status)
        assertEquals("c-$suffix@transaction.test", store.execute(UniqueModel.get(ka)).values.single().values[UniqueModel.ref { email }])
        assertEquals("a-$suffix@transaction.test", store.execute(UniqueModel.get(kb)).values.single().values[UniqueModel.ref { email }])
        assertEquals(kb, store.execute(UniqueModel.scan(where = Equals(UniqueModel.ref { email } with "a-$suffix@transaction.test"), allowTableScan = true)).values.single().key)
    } finally { store.execute(UniqueModel.delete(ka, kb, hardDelete = true)) }
}

private suspend fun transactionCollectionDeltasReplayOnce(store: IsDataStore) {
    val values = TestMarykModel.create {
        string with "haha-collections"; int with 1; uint with Random.nextInt().toUInt()
        double with 1.0; dateTime with LocalDateTime(2026, 10, 11, 0, 0); bool with false
        listOfString with listOf("a"); incMap with mapOf(1u to "original"); setOfString with setOf("a"); map with mapOf(LocalTime(1, 0) to "original")
    }
    val key = TestMarykModel.key(values)
    val tx = store.beginTransaction()
    try {
        tx.execute(TestMarykModel.add(key to values))
        val step = tx.execute(TestMarykModel.change(key.change(ListChange(TestMarykModel.ref { listOfString }.change(addValuesToEnd = listOf("b"))),
            IncMapChange(TestMarykModel.ref { incMap }.change(addValues = listOf("second"))),
            SetChange(TestMarykModel.ref { setOfString }.change(addValues = setOf("b"))),
            Change(TestMarykModel.ref { map.at(LocalTime(1, 0)) } with "changed"))))
        val generated = assertIs<ChangeSuccess<*>>(step.response.statuses.single()).changes?.filterIsInstance<IncMapAddition>()?.single()?.additions?.single()?.addedValues
        // Generated values belong to this response, independently of the retained journal.
        (generated as? MutableList<*>)?.clear()
        val first = tx.execute(TestMarykModel.get(key)).response.values.single()
        assertEquals(listOf("a", "b"), first.values[TestMarykModel.ref { listOfString }])
        assertEquals(mapOf(1u to "original", 2u to "second"), first.values[TestMarykModel.ref { incMap }])
        tx.execute(TestMarykModel.change(key.change(ListChange(TestMarykModel.ref { listOfString }.change(addValuesToEnd = listOf("c"))), lastVersion = first.lastVersion)))
        repeat(2) { assertEquals(listOf("a", "b", "c"), tx.execute(TestMarykModel.get(key)).response.values.single().values[TestMarykModel.ref { listOfString }]) }
        assertEquals(TransactionStatus.Committed, tx.commit().status)
        val durable = store.execute(TestMarykModel.get(key)).values.single()
        assertEquals(listOf("a", "b", "c"), durable.values[TestMarykModel.ref { listOfString }])
        assertEquals(mapOf(1u to "original", 2u to "second"), durable.values[TestMarykModel.ref { incMap }])
        assertEquals(setOf("a", "b"), durable.values[TestMarykModel.ref { setOfString }])
        assertEquals(mapOf(LocalTime(1, 0) to "changed"), durable.values[TestMarykModel.ref { map }])
    } finally { store.execute(TestMarykModel.delete(key, hardDelete = true)) }
}

private suspend fun transactionCreationThroughChange(store: IsDataStore) {
    val key = SimpleMarykModel.key(SimpleMarykModel.create { value with "haha-create-change" })
    val tx = store.beginTransaction()
    assertFailsWith<RequestException> { tx.execute(SimpleMarykModel.change(key.change(ObjectCreate, Change(SimpleMarykModel.ref { value } with "haha-created")))) }
    tx.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "haha-created" }))
    assertEquals("haha-created", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[SimpleMarykModel.ref { value }])
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-created", store.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
}

/** A general ordered journal receipt, including provisional-version rebinding, survives reopen. */
suspend fun createTransactionReceiptForReopen(store: IsDataStore): Pair<TransactionRequest, TransactionResponse> {
    val values = SimpleMarykModel.create { value with "haha-durable-journal" }
    val key = SimpleMarykModel.key(values)
    val id = "journal-$key"
    val opened = store.executeTransaction(TransactionRequest(id))
    assertEquals(TransactionStatus.Staged, opened.status)
    val request = TransactionRequest(id, listOf(
        SimpleMarykModel.add(key to values),
        SimpleMarykModel.get(key),
        SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-durable-final"), lastVersion = requireNotNull(opened.version) + 1uL)),
        Person.add(Person.create { firstName with "Journal"; surname with "Durable" }),
    ), opened.revision, opened.version, commit = true)
    val response = store.executeTransaction(request)
    assertEquals(TransactionStatus.Committed, response.status)
    assertEquals(3, response.responses.size)
    assertEquals("haha-durable-final", store.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
    return request to response
}

private suspend fun transactionGeneratedValuesSurviveCommitRecovery(store: IsDataStore) {
    val values = TestMarykModel.create {
        string with "haha-generated"; int with 1; uint with Random.nextInt().toUInt()
        double with 1.0; dateTime with LocalDateTime(2026, 10, 11, 0, 0); bool with false
        incMap with mapOf(1u to "original")
    }
    val key = TestMarykModel.key(values)
    var lose = true
    var committed: TransactionResponse? = null
    val transport = object : IsDataStore by store, TransactionalDataStore {
        override val transactionCapabilities get() = store.transactionCapabilities
        override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
            val result = store.executeTransaction(request)
            if (request.commit && lose) { lose = false; committed = result; error("Lost generated-value response") }
            return result
        }
    }
    val tx = transport.beginTransaction()
    try {
        tx.execute(TestMarykModel.add(key to values))
        tx.execute(TestMarykModel.change(key.change(IncMapChange(TestMarykModel.ref { incMap }.change(addValues = listOf("second"))))))
        tx.execute(TestMarykModel.change(key.change(IncMapChange(TestMarykModel.ref { incMap }.change(addValues = listOf("third"))))))
        assertEquals(TransactionStatus.OutcomeUnknown, tx.commit().status)
        val recovered = tx.resolveCommit()
        assertEquals(TransactionStatus.Committed, recovered.status)
        val generated = recovered.responses.drop(1).map { response ->
            val change = assertIs<ChangeResponse<*>>(response)
            assertIs<ChangeSuccess<*>>(change.statuses.single()).changes!!.filterIsInstance<IncMapAddition>().single().additions.single().addedValues
        }
        assertEquals(listOf(listOf("second"), listOf("third")), generated)
        assertEquals(committed, recovered)
        assertEquals(mapOf(1u to "original", 2u to "second", 3u to "third"), store.execute(TestMarykModel.get(key)).values.single().values[TestMarykModel.ref { incMap }])
    } finally { store.execute(TestMarykModel.delete(key, hardDelete = true)) }
}

private suspend fun transactionDuplicateTargetsWithinOneRequest(store: IsDataStore) {
    val values = SimpleMarykModel.create { value with "haha-duplicate-target" }
    val key = SimpleMarykModel.key(values)
    val tx = store.beginTransaction()
    tx.execute(SimpleMarykModel.add(key to values))
    assertFailsWith<RequestException> { tx.execute(SimpleMarykModel.change(
        key.change(Change(SimpleMarykModel.ref { value } with "haha-should-not-apply")),
        key.change(Check(SimpleMarykModel.ref { value } with "haha-wrong-check")),
    )) }
    assertFailsWith<RequestException> { tx.execute(SimpleMarykModel.delete(key, key)) }
    assertEquals("haha-duplicate-target", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[SimpleMarykModel.ref { value }])
    tx.executeBatch(listOf(
        SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-first-valid"))),
        SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-final-valid"))),
    ))
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals("haha-final-valid", store.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
}
