package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationOutcome
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.beginTransaction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object PendingSource : RootDataModel<PendingSource>(name = "PendingRecord", version = Version(1)) {
    val value by string(index = 1u)
}

private object PendingTarget : RootDataModel<PendingTarget>(name = "PendingRecord", version = Version(2)) {
    val value by number(index = 1u, type = SInt32)
}

private object PendingOtherTarget : RootDataModel<PendingOtherTarget>(name = "PendingRecord", version = Version(2)) {
    val value by number(index = 1u, type = SInt64)
}

class PendingMigrationSchemaTest {
    @Test
    fun failedMigrationFencesOldReadsAndBindsTheExactTarget() = runTest {
        installIndexedDbForTests()
        for (history in listOf(false, true)) {
            val database = "maryk-pending-target-$history-${Random.nextInt()}"
            val old = IndexedDbDataStore.open(database, mapOf(1u to PendingSource), keepAllVersions = history)
            var inserted: AddSuccess<PendingTarget>? = null
            try {
                val transaction = old.beginTransaction(readOnly = true)
                assertFailsWith<RequestException> {
                    IndexedDbDataStore.open(database, mapOf(1u to PendingTarget), keepAllVersions = history,
                        migrationConfiguration = MigrationConfiguration(migrationHandler = { context ->
                            inserted = assertIs<AddSuccess<PendingTarget>>(context.store.execute(
                                PendingTarget.add(PendingTarget.create { value with 123456789 })
                            ).statuses.single())
                            MigrationOutcome.Fatal("injected failure after target data commit")
                        }))
                }
                val added = assertNotNull(inserted)
                val oldKey = Key<PendingSource>(added.key.bytes)
                assertFailsWith<RequestException> { old.execute(PendingSource.get(oldKey)) }
                assertFailsWith<RequestException> { old.execute(PendingSource.scan(allowTableScan = true)) }
                if (history) assertFailsWith<RequestException> { old.execute(PendingSource.getChanges(oldKey)) }
                assertFailsWith<TransactionException> { old.beginTransaction() }
                assertFailsWith<TransactionException> { old.beginTransaction(readOnly = true) }
                assertFailsWith<TransactionException> { transaction.execute(PendingSource.get(oldKey)) }
                transaction.rollback()
                assertFailsWith<RequestException> { IndexedDbDataStore.open(database, mapOf(1u to PendingSource), keepAllVersions = history) }
                assertFailsWith<MigrationException> {
                    IndexedDbDataStore.open(database, mapOf(1u to PendingOtherTarget), keepAllVersions = history,
                        migrationConfiguration = MigrationConfiguration(migrationHandler = {
                            error("Same-version replacement target must never run")
                        }))
                }
                val identityKey = migrationTargetMetadataKey(1u)
                val identity = assertNotNull(old.byteStore.get("meta", identityKey))
                old.byteStore.delete("meta", identityKey)
                val missingIdentity = assertFailsWith<MigrationException> {
                    IndexedDbDataStore.open(database, mapOf(1u to PendingTarget), keepAllVersions = history,
                        migrationConfiguration = MigrationConfiguration(migrationHandler = {
                            error("Unbound legacy progress must never run")
                        }))
                }
                assertTrue(missingIdentity.message.orEmpty().contains("authoritative"))
                // This fixture retained the authoritative original identity; real legacy jobs may not have it.
                old.byteStore.put("meta", identityKey, identity)
                val current = IndexedDbDataStore.open(database, mapOf(1u to PendingTarget), keepAllVersions = history,
                    migrationConfiguration = MigrationConfiguration(migrationHandler = { context ->
                        assertEquals(123456789, context.store.execute(PendingTarget.get(added.key)).values.single().values { value })
                        MigrationOutcome.Success
                    }))
                try {
                    assertEquals(123456789, current.execute(PendingTarget.get(added.key)).values.single().values { value })
                    assertNull(current.byteStore.get("meta", identityKey))
                    assertNull(current.byteStore.get("meta", migrationStateMetadataKey(1u)))
                    assertFailsWith<RequestException> { old.execute(PendingSource.get(oldKey)) }
                } finally { current.close() }
                IndexedDbDataStore.open(database, mapOf(1u to PendingTarget), keepAllVersions = history).close()
            } finally { old.close() }
        }
    }
}
