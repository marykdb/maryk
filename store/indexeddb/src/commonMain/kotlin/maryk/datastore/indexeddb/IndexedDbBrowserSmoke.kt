package maryk.datastore.indexeddb

import kotlinx.coroutines.delay
import kotlin.random.Random

/** Executes a representative Maryk IndexedDB flow from a browser entrypoint. */
suspend fun runIndexedDbBrowserSmoke(
    databaseName: String = "maryk-browser-smoke-${Random.nextInt()}",
) {
    val initial = openIndexedDbByteStore(databaseName, setOf("records"))
    try {
        initial.put("records", byteArrayOf(1), byteArrayOf(10))
        initial.put("records", byteArrayOf(2), byteArrayOf(20))
        check(initial.get("records", byteArrayOf(1))!!.contentEquals(byteArrayOf(10))) { "put/get" }
        check(initial.scan("records").map { it.first.single() } == listOf(1, 2)) { "scan" }
        initial.delete("records", byteArrayOf(1))
        check(initial.get("records", byteArrayOf(1)) == null) { "delete" }
    } finally {
        initial.close()
    }
    delay(1)

    val migrated = openIndexedDbByteStore(databaseName, setOf("records", "migration"), version = 2)
    try {
        migrated.put("migration", byteArrayOf(3), byteArrayOf(30))
        check(migrated.get("migration", byteArrayOf(3))!!.contentEquals(byteArrayOf(30))) { "migration" }

        migrated.transaction(setOf("records"), IndexedDbTransactionMode.READWRITE) {
            migrated.put("records", byteArrayOf(4), byteArrayOf(40))
        }
        check(migrated.get("records", byteArrayOf(4))!!.contentEquals(byteArrayOf(40))) { "locking" }
    } finally {
        migrated.close()
    }
}
