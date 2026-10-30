package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.executeAtomic
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

object InterruptedAtomicRecord : RootDataModel<InterruptedAtomicRecord>(indexes = {
    listOf(InterruptedAtomicRecord.group.ref())
}) {
    val group by string(1u)
    val token by string(2u, unique = true)
    val items by list(3u, valueDefinition = StringDefinition())
    val allocated by incrementingMap(4u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val secret by string(5u, sensitive = true)
}

val interruptedAtomicModels = mapOf(1u to InterruptedAtomicRecord)

fun interruptedAtomicKey(id: Int): Key<InterruptedAtomicRecord> = InterruptedAtomicRecord.key(
    ByteArray(InterruptedAtomicRecord.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() }
)

/** A real cipher with an injectable failure/barrier at the persisted field boundary. */
class AtomicInterruptionEncryptionProvider : ContextualFieldEncryptionProvider {
    private val cipher = AesGcmHmacSha256EncryptionProvider(
        ByteArray(32) { (it + 1).toByte() }, ByteArray(32) { (it + 33).toByte() },
    )
    var beforeEncrypt: (suspend (FieldEncryptionContext?) -> Unit)? = null

    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        beforeEncrypt?.invoke(null)
        return cipher.encrypt(value, offset, length)
    }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int) = cipher.decrypt(value, offset, length)

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray {
        beforeEncrypt?.invoke(context)
        return cipher.encrypt(context, value, offset, length)
    }

    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) =
        cipher.decrypt(context, value, offset, length)
}

suspend fun seedInterruptedAtomicRecords(store: IsDataStore) = seedInterruptedAtomicRecords(store, null)

suspend fun seedInterruptedAtomicRecords(store: IsDataStore, version: ULong?) {
    val values = listOf("alpha" to "before-a", "beta" to "before-b").mapIndexed { index, (tokenValue, secretValue) ->
        interruptedAtomicKey(index + 1) to InterruptedAtomicRecord.create {
            group with "before"
            token with tokenValue
            items with listOf("first")
            allocated with mapOf(1u to "first")
            secret with secretValue
        }
    }
    if (version == null) {
        store.execute(InterruptedAtomicRecord.add(*values.toTypedArray())).statuses.forEach {
            assertIs<AddSuccess<InterruptedAtomicRecord>>(it)
        }
    } else {
        for ((key, value) in values) {
            val response = store.processUpdate(UpdateResponse(InterruptedAtomicRecord,
                AdditionUpdate(key, version, version, 0, false, value)))
            assertIs<AddSuccess<InterruptedAtomicRecord>>(assertIs<AddResponse<InterruptedAtomicRecord>>(response.result).statuses.single())
        }
    }
}

/** The second member encrypts only after the first member has staged all its mutations. */
fun interruptedAtomicRequest() = AtomicRequest(
    "process-interrupted-atomic",
    InterruptedAtomicRecord.change(interruptedAtomicKey(1).change(
        Change(InterruptedAtomicRecord.ref { group } with "after", InterruptedAtomicRecord.ref { token } with "gamma"),
        ListChange(InterruptedAtomicRecord.ref { items }.change(addValuesToEnd = listOf("second"))),
        IncMapChange(InterruptedAtomicRecord.ref { allocated }.change(addValues = listOf("second"))),
    )),
    InterruptedAtomicRecord.change(interruptedAtomicKey(2).change(
        Change(InterruptedAtomicRecord.ref { secret } with "after-b"),
    )),
)

fun interruptedAtomicVersion(response: AtomicResponse): ULong = assertIs<ChangeSuccess<*>>(
    assertIs<ChangeResponse<*>>(response.responses.first()).statuses.single(),
).version

suspend fun assertInterruptedAtomicState(store: IsDataStore, committed: Boolean) {
    val first = store.execute(InterruptedAtomicRecord.get(interruptedAtomicKey(1))).values.single()
    val second = store.execute(InterruptedAtomicRecord.get(interruptedAtomicKey(2))).values.single()
    assertEquals(if (committed) "after" else "before", first.values { group })
    assertEquals(if (committed) "gamma" else "alpha", first.values { token })
    assertEquals(if (committed) listOf("first", "second") else listOf("first"), first.values { items })
    assertEquals(if (committed) mapOf(1u to "first", 2u to "second") else mapOf(1u to "first"), first.values { allocated })
    assertEquals("before-a", first.values { secret })
    assertEquals("before", second.values { group })
    assertEquals("beta", second.values { token })
    assertEquals(if (committed) "after-b" else "before-b", second.values { secret })
    assertEquals(2, store.execute(InterruptedAtomicRecord.scan(allowTableScan = true)).values.size)

    val afterKeys = store.execute(InterruptedAtomicRecord.scan(
        where = Equals(InterruptedAtomicRecord.ref { group } with "after"),
    )).values.map { it.key }.toSet()
    assertEquals(if (committed) setOf(interruptedAtomicKey(1)) else emptySet(), afterKeys)
    val beforeKeys = store.execute(InterruptedAtomicRecord.scan(
        where = Equals(InterruptedAtomicRecord.ref { group } with "before"),
    )).values.map { it.key }.toSet()
    assertEquals(if (committed) setOf(interruptedAtomicKey(2)) else setOf(interruptedAtomicKey(1), interruptedAtomicKey(2)), beforeKeys)
    for ((tokenValue, expectedKeys) in listOf(
        "alpha" to if (committed) emptyList() else listOf(interruptedAtomicKey(1)),
        "gamma" to if (committed) listOf(interruptedAtomicKey(1)) else emptyList(),
        "beta" to listOf(interruptedAtomicKey(2)),
    )) {
        assertEquals(expectedKeys, store.execute(InterruptedAtomicRecord.scan(
            where = Equals(InterruptedAtomicRecord.ref { token } with tokenValue),
        )).values.map { it.key }, "unique owner for $tokenValue")
    }
    for (row in listOf(first, second)) {
        val history = store.execute(InterruptedAtomicRecord.getChanges(
            row.key, toVersion = ULong.MAX_VALUE, maxVersions = 10u,
        )).changes.single().changes
        assertEquals(if (committed) 2 else 1, history.size)
        assertEquals(row.firstVersion, history.first().version)
        assertEquals(row.lastVersion, history.last().version)
        if (!committed) assertEquals(row.firstVersion, row.lastVersion)
    }
    if (committed) assertEquals(first.lastVersion, second.lastVersion)
}

/** Replay must preserve the original response and cannot append another allocated key. */
suspend fun completeInterruptedAtomicOnce(store: IsDataStore, expectedVersion: ULong? = null): AtomicResponse {
    val committed = store.executeAtomic(interruptedAtomicRequest())
    assertEquals(AtomicStatus.Committed, committed.status)
    val version = interruptedAtomicVersion(committed)
    if (expectedVersion != null) assertEquals(expectedVersion, version)
    assertInterruptedAtomicState(store, committed = true)
    assertEquals(committed, store.executeAtomic(interruptedAtomicRequest()))
    assertInterruptedAtomicState(store, committed = true)
    val third = assertIs<ChangeSuccess<InterruptedAtomicRecord>>(store.execute(InterruptedAtomicRecord.change(
        interruptedAtomicKey(1).change(IncMapChange(InterruptedAtomicRecord.ref { allocated }.change(addValues = listOf("third")))),
    )).statuses.single())
    assertTrue(third.version > version)
    assertEquals(mapOf(1u to "first", 2u to "second", 3u to "third"), store.execute(
        InterruptedAtomicRecord.get(interruptedAtomicKey(1)),
    ).values.single().values { allocated })
    return committed
}

/** An external cipher failure is injected after an earlier member staged its full changes. */
suspend fun assertAtomicEncryptionFailureRollsBack(store: IsDataStore, provider: AtomicInterruptionEncryptionProvider) {
    seedInterruptedAtomicRecords(store)
    var injected = 0
    provider.beforeEncrypt = { context ->
        if (context?.recordKey?.contentEquals(interruptedAtomicKey(2).bytes) == true) {
            injected++
            throw IllegalStateException("Injected precommit field encryption failure")
        }
    }
    val failed = try {
        runCatching { store.executeAtomic(interruptedAtomicRequest()) }
    } finally {
        provider.beforeEncrypt = null
    }
    assertTrue(injected > 0, "The second transaction member must reach the cipher boundary")
    // Unexpected provider failures may conservatively report an unknown outcome;
    // the recovery contract is resolved by reading and retrying the exact request.
    failed.getOrNull()?.let {
        assertTrue(it.status == AtomicStatus.Rejected || it.status == AtomicStatus.OutcomeUnknown)
    }
    assertInterruptedAtomicState(store, committed = false)
    completeInterruptedAtomicOnce(store)
}
