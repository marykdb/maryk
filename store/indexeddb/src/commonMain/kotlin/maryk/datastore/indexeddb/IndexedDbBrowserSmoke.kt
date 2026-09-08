package maryk.datastore.indexeddb

import kotlinx.coroutines.delay
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.core.query.requests.add
import maryk.core.query.requests.get
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
        val scannedKeys = initial.scan("records").map { it.first.single() }
        check(scannedKeys == listOf<Byte>(1, 2)) { "scan: $scannedKeys" }
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

    val dataStore = IndexedDbDataStore.open(
        databaseName = "$databaseName-datastore",
        dataModelsById = mapOf(1u to IndexedDbBrowserSmokeModel),
    )
    try {
        val values = IndexedDbBrowserSmokeModel.create { value with "browser" }
        dataStore.execute(IndexedDbBrowserSmokeModel.add(values))
        // WebKit exposes a completed write transaction to another connection on a later event-loop turn.
        delay(1)

        val secondContext = IndexedDbDataStore.open(
            databaseName = "$databaseName-datastore",
            dataModelsById = mapOf(1u to IndexedDbBrowserSmokeModel),
        )
        try {
            check(secondContext.execute(IndexedDbBrowserSmokeModel.get(IndexedDbBrowserSmokeModel.key(values))).values.isNotEmpty()) {
                "datastore cross-context read"
            }
        } finally {
            secondContext.close()
        }
    } finally {
        dataStore.close()
    }
}

private object IndexedDbBrowserSmokeModel : RootDataModel<IndexedDbBrowserSmokeModel>() {
    val value by string(index = 1u)
}
