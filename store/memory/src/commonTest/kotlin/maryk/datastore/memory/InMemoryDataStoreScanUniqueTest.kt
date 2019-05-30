package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.lib.time.Date
import maryk.test.assertType
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.Properties.string
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

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
            multi=TypedValue(T2, 22),
            booleanForKey= true,
            dateForKey= Date(2018, 3, 29),
            multiForKey= TypedValue(S1, "hii"),
            enumEmbedded= E1
        )
    )

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                CompleteMarykModel.add(*objects)
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<CompleteMarykModel>>(status)
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
                where = Equals(
                    string.ref() with "haas"
                )
            )
        )

        expect(1) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(objects[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }
}
