package maryk.datastore.test

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backup
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.restore
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

val atomicRestoreModels: Map<UInt, IsRootDataModel> = mapOf(1u to UniqueModel, 2u to SimpleMarykModel)

class AtomicRestoreBackup : DataStoreBackupReader, DataStoreBackupWriter {
    override lateinit var manifest: DataStoreBackupManifest
    val chunks = mutableListOf<DataStoreBackupChunk>()
    override suspend fun begin(manifest: DataStoreBackupManifest) { this.manifest = manifest }
    override suspend fun write(chunk: DataStoreBackupChunk) { chunks += chunk }
    override suspend fun complete() = Unit
    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) { chunks.forEach { consumer(it) } }
}

/** Runs against each backend; receiver/owner key order and chunk size cannot change replay semantics. */
suspend fun checkAtomicRestore(source: IsDataStore, target: IsDataStore, receiverFirst: Boolean, batchSize: UInt) {
    val owner = UniqueModel.key(ByteArray(16) { if (it == 15) (if (receiverFirst) 2 else 1).toByte() else 0 })
    val receiver = UniqueModel.key(ByteArray(16) { if (it == 15) (if (receiverFirst) 1 else 2).toByte() else 0 })
    source.execute(UniqueModel.add(owner to UniqueModel.create { email with "transfer@test.com" }))
    val atomic = source.executeAtomic(AtomicRequest("restore-transfer",
        UniqueModel.change(owner.change(Change(UniqueModel.ref { email } with "released@test.com"))),
        UniqueModel.add(receiver to UniqueModel.create { email with "transfer@test.com" }),
        SimpleMarykModel.add(SimpleMarykModel.create { value with "ha same atomic version" }),
    ))
    assertEquals(AtomicStatus.Committed, atomic.status)
    val version = source.execute(UniqueModel.get(receiver)).values.single().lastVersion
    val backup = AtomicRestoreBackup()
    source.backup(backup, snapshotVersion = version + 1uL, batchSize = batchSize)
    assertEquals(3uL, target.restore(backup).records)
    for (key in listOf(owner, receiver)) {
        assertEquals(source.execute(UniqueModel.getChanges(key)).changes, target.execute(UniqueModel.getChanges(key)).changes)
    }
    for (atVersion in listOf(null, version, version - 1uL)) {
        val actual = target.execute(UniqueModel.scan(where = Equals(UniqueModel.ref { email } with "transfer@test.com"), toVersion = atVersion))
        assertEquals(listOf(if (atVersion == version - 1uL) owner else receiver), actual.values.map { it.key })
    }
    assertEquals(1, target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
}

/** A repeated-key source transaction can swap values, so sorting dependencies alone is insufficient. */
suspend fun checkAtomicRestoreSwap(source: IsDataStore, target: IsDataStore) {
    val first = UniqueModel.key(ByteArray(16) { if (it == 15) 1 else 0 })
    val second = UniqueModel.key(ByteArray(16) { if (it == 15) 2 else 0 })
    source.execute(UniqueModel.add(first to UniqueModel.create { email with "a@test.com" }, second to UniqueModel.create { email with "b@test.com" }))
    val transaction = source.beginTransaction()
    transaction.execute(UniqueModel.change(first.change(Change(UniqueModel.ref { email } with "temporary@test.com"))))
    transaction.execute(UniqueModel.change(second.change(Change(UniqueModel.ref { email } with "a@test.com"))))
    transaction.execute(UniqueModel.change(first.change(Change(UniqueModel.ref { email } with "b@test.com"))))
    assertEquals(TransactionStatus.Committed, transaction.commit().status)
    val backup = AtomicRestoreBackup()
    source.backup(backup, snapshotVersion = source.execute(UniqueModel.get(first)).values.single().lastVersion + 1uL, batchSize = 1u)
    target.restore(backup)
    assertEquals("b@test.com", target.execute(UniqueModel.get(first)).values.single().values { email })
    assertEquals("a@test.com", target.execute(UniqueModel.get(second)).values.single().values { email })
    for (key in listOf(first, second)) assertEquals(source.execute(UniqueModel.getChanges(key)).changes, target.execute(UniqueModel.getChanges(key)).changes)
}

/** A malformed group's first record must not be published when a later record conflicts. */
suspend fun checkInvalidAtomicRestore(source: IsDataStore, target: IsDataStore) {
    source.execute(UniqueModel.add(UniqueModel.create { email with "a@test.com" }, UniqueModel.create { email with "b@test.com" }))
    val backup = AtomicRestoreBackup()
    source.backup(backup, snapshotVersion = source.execute(UniqueModel.scan(allowTableScan = true)).values.maxOf { it.lastVersion } + 1uL)
    val duplicate = backup.chunks.single().records.map { record ->
        record.copy(changes = record.changes.map { version -> version.copy(changes = version.changes.map {
            if (it is Change) Change(UniqueModel.ref { email } with "duplicate@test.com") else it
        }) })
    }
    backup.chunks[0] = backup.chunks.single().copy(records = duplicate)
    assertFails { target.restore(backup) }
    assertTrue(target.execute(UniqueModel.scan(allowTableScan = true)).values.isEmpty())
}
