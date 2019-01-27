package maryk.datastore.memory

import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.values.Values
import maryk.lib.time.DateTime
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

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

        addResponse.dataModel shouldBe Log
        addResponse.statuses.count() shouldBe 4

        val keysToOriginal = mutableMapOf<Key<*>, Values<Log, *>>()
        val keys = mutableListOf<Key<Log>>()
        addResponse.statuses.forEachIndexed { index, it ->
            val response = shouldBeOfType<AddSuccess<Log>>(it)
            shouldBeRecent(response.version, 1000uL)
            response.changes.isEmpty() shouldBe true
            shouldBeOfType<Key<Log>>(response.key).size shouldBe 12
            keys.add(response.key)
            keysToOriginal[response.key] = logs[index]
        }

        val getResponse = dataStore.execute(
            Log.get(*keys.toTypedArray())
        )

        getResponse.values.size shouldBe 4

        getResponse.values.forEachIndexed { index, value ->
            value.values shouldBe logs[index]
        }
    }

    @Test
    fun notAddSameObjectTwice() = runSuspendingTest {
        val log = Log("I am unique", timestamp = DateTime(2018, 9, 9, 14, 30, 0, 0))

        val addResponse = dataStore.execute(
            Log.add(log)
        )

        addResponse.dataModel shouldBe Log
        addResponse.statuses.count() shouldBe 1

        val key = Log.key(log)

        val addResponseAgain = dataStore.execute(
            Log.add(log)
        )

        addResponseAgain.dataModel shouldBe Log
        addResponseAgain.statuses.count() shouldBe 1

        addResponseAgain.statuses[0] shouldBe AlreadyExists(key)
    }
}
