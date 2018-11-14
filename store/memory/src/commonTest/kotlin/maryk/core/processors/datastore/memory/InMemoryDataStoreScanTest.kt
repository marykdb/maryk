@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory

import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
class InMemoryDataStoreScanTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<Log>>()

    private val logs = arrayOf(
        Log("Something happened", timestamp = DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", timestamp = DateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("WRONG", ERROR, DateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                Log.add(*logs)
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<Log>>(status)
                keys.add(response.key)
            }
        }
    }

    @Test
    fun executeAddAndSimpleScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[1])
        )

        scanResponse.values.size shouldBe 2

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.values[0].let {
            it.values shouldBe logs[1]
            it.key shouldBe keys[1]
        }
        scanResponse.values[1].let {
            it.values shouldBe logs[0]
            it.key shouldBe keys[0]
        }
    }
}
