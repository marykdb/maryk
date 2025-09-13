package maryk.datastore.test

import kotlinx.datetime.LocalDate
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.expect

class DataStoreGetSelectTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<CompleteMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeGetWithSelect" to ::executeGetWithSelect,
    )

    private val objects = arrayOf(
        CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create { value with "haha" }
            multi with TypedValue(T2, 22)
            booleanForKey with true
            dateForKey with LocalDate(2018, 3, 29)
            multiForKey with TypedValue(S1, "hii")
            enumEmbedded with E1
        }
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            CompleteMarykModel.add(*objects)
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<CompleteMarykModel>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            CompleteMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeGetWithSelect() {
        val getResponse = dataStore.execute(
            CompleteMarykModel.get(
                keys[0],
                select = CompleteMarykModel.graph { listOf(
                    number,
                    subModel,
                ) }
            )
        )

        expect(1) { getResponse.values.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        getResponse.values[0].let {
            expect(
                CompleteMarykModel.create(setDefaults = false) {
                    number with 24u
                    subModel with {
                        value with "haha"
                    }
                }
            ) { it.values }
            expect(keys[0]) { it.key }
        }
    }
}
