package maryk.datastore.memory

import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.values.Values
import maryk.lib.time.DateTime
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

class InMemoryDataStoreAddTest {
    private val dataStore = InMemoryDataStore()

    private val logs = arrayOf(
        Log("Something happened", timestamp = DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", timestamp = DateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", timestamp = DateTime(2018, 11, 14, 12, 33, 22, 111)),
        Log("WRONG", ERROR, DateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    @Test
    fun executeAddAndSimpleGetRequest() = runSuspendingTest {
        val addResponse = dataStore.execute(
            Log.add(*logs)
        )

        expect(Log) { addResponse.dataModel }
        expect(4) { addResponse.statuses.count() }

        val keysToOriginal = mutableMapOf<Key<*>, Values<Log, *>>()
        val keys = mutableListOf<Key<Log>>()
        addResponse.statuses.forEachIndexed { index, it ->
            val response = assertType<AddSuccess<Log>>(it)
            shouldBeRecent(response.version, 1000uL)
            assertTrue { response.changes.isEmpty() }
            expect(11) { assertType<Key<Log>>(response.key).size }
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

    @Test
    fun notAddSameObjectTwice() = runSuspendingTest {
        val log = Log("I am unique", timestamp = DateTime(2018, 9, 9, 14, 30, 0, 0))

        val addResponse = dataStore.execute(
            Log.add(log)
        )

        expect(Log) { addResponse.dataModel }
        expect(1) { addResponse.statuses.count() }

        val key = Log.key(log)

        val addResponseAgain = dataStore.execute(
            Log.add(log)
        )

        expect(Log) { addResponseAgain.dataModel }
        expect(1) { addResponseAgain.statuses.count() }

        expect(AlreadyExists(key)) { addResponseAgain.statuses[0] }
    }
}
