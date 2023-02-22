package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.expect

class DataStoreAddTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    override val allTests = mapOf(
        "executeAddAndSimpleGetRequest" to ::executeAddAndSimpleGetRequest,
        "notAddSameObjectTwice" to ::notAddSameObjectTwice
    )

    private val keys = mutableListOf<Key<RootDataModel<Log>>>()

    private val logs = arrayOf(
        Log("Something happened", timestamp = LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000)),
        Log("Something else happened", timestamp = LocalDateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", timestamp = LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)),
        Log("WRONG", ERROR, LocalDateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    override suspend fun resetData() {
        dataStore.execute(
            Log.Model.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
    }

    private suspend fun executeAddAndSimpleGetRequest() {
        val addResponse = dataStore.execute(
            Log.Model.add(*logs)
        )

        expect(Log.Model) { addResponse.dataModel }
        expect(4) { addResponse.statuses.count() }

        val keysToOriginal = mutableMapOf<Key<*>, Values<RootDataModel<Log>, *>>()
        addResponse.statuses.forEachIndexed { index, it ->
            val response = assertIs<AddSuccess<RootDataModel<Log>>>(it)
            assertRecent(response.version, 1000uL)
            assertTrue { response.changes.isEmpty() }
            expect(11) { assertIs<Key<RootDataModel<Log>>>(response.key).size }
            keys.add(response.key)
            keysToOriginal[response.key] = logs[index]
        }

        val getResponse = dataStore.execute(
            Log.get(*keys.toTypedArray())
        )

        expect(4) { getResponse.values.size }

        getResponse.values.forEachIndexed { index, value ->
            expect(logs[index]) { value.values }
        }
    }

    private suspend fun notAddSameObjectTwice() {
        val log = Log("I am unique", timestamp = LocalDateTime(2018, 9, 9, 14, 30, 0, 0))
        val key = Log.key(log)
        keys += key // make sure data gets cleaned

        val addResponse = dataStore.execute(
            Log.Model.add(log)
        )

        expect(Log.Model) { addResponse.dataModel }
        expect(1) { addResponse.statuses.count() }

        val addResponseAgain = dataStore.execute(
            Log.Model.add(log)
        )

        expect(Log.Model) { addResponseAgain.dataModel }
        expect(1) { addResponseAgain.statuses.count() }

        expect(AlreadyExists(key)) { addResponseAgain.statuses[0] }
    }
}
