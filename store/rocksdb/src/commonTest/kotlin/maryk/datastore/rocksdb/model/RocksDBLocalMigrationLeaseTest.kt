package maryk.datastore.rocksdb.model

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RocksDBLocalMigrationLeaseTest {
    @Test
    fun lockContentionAndRelease() = runTest {
        val storePath = "lease-test-store"
        val leaseA = RocksDBLocalMigrationLease(storePath)
        val leaseB = RocksDBLocalMigrationLease(storePath)

        assertTrue(leaseA.tryAcquire(1u, "m1"))
        assertFalse(leaseB.tryAcquire(1u, "m2"))

        leaseA.release(1u, "m1")
        assertTrue(leaseB.tryAcquire(1u, "m2"))
    }

    @Test
    fun sameMigrationIdCanReacquire() = runTest {
        val storePath = "lease-test-store-same-migration"
        val leaseA = RocksDBLocalMigrationLease(storePath)
        val leaseB = RocksDBLocalMigrationLease(storePath)

        assertTrue(leaseA.tryAcquire(1u, "m1"))
        assertTrue(leaseB.tryAcquire(1u, "m1"))
    }
}
