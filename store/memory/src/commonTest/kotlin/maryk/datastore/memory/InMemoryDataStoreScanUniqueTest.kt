package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.lib.time.Date
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.Properties.string
import maryk.test.models.MarykEnum.O1
import maryk.test.models.MarykEnum.O2
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.SimpleMarykModel
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class InMemoryDataStoreScanUniqueTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<CompleteMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    private val objects = arrayOf(
        CompleteMarykModel(
            string="haas",
            number = 24u,
            subModel = SimpleMarykModel(
                value = "haha"
            ),
            multi=TypedValue(O2, true),
            booleanForKey= true,
            dateForKey= Date(2018, 3, 29),
            multiForKey= TypedValue(O1, "hii"),
            enumEmbedded= E1
        )
    )

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                CompleteMarykModel.add(*objects)
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<CompleteMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    @Test
    fun executeSimpleScanFilterRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            CompleteMarykModel.scan(
                filter = Equals(
                    string.ref() with "haas"
                )
            )
        )

        scanResponse.values.size shouldBe 1

        scanResponse.values[0].let {
            it.values shouldBe objects[0]
            it.key shouldBe keys[0]
        }
    }
}
