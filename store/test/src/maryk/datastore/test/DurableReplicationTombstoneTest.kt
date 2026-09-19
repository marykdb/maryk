package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.models.key
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Log
import kotlin.test.assertTrue

class DurableReplicationTombstoneTest {
    private val timestamp = LocalDateTime(2022, 2, 4, 0, 0)
    private val values = Log("durable replication tombstone", timestamp = timestamp)
    private val key = Log.key(values)

    suspend fun writeHardDelete(dataStore: IsDataStore) {
        dataStore.processUpdate(
            UpdateResponse(
                dataModel = Log,
                update = AdditionUpdate(key, 10uL, 10uL, 1, false, values),
            )
        )
        dataStore.processUpdate(
            UpdateResponse(
                dataModel = Log,
                update = RemovalUpdate(key, 20uL, HardDelete),
            )
        )
    }

    suspend fun replayStaleAdd(dataStore: IsDataStore) {
        dataStore.processUpdate(
            UpdateResponse(
                dataModel = Log,
                update = AdditionUpdate(key, 10uL, 10uL, 1, false, values),
            )
        )
        assertTrue(dataStore.execute(Log.get(key)).values.isEmpty())
    }
}
