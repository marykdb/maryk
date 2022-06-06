package maryk.datastore.test

import kotlinx.datetime.LocalDate
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.assertType
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.Properties.string
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.expect

class DataStoreScanUniqueTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<CompleteMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleScanFilterRequest" to ::executeSimpleScanFilterRequest,
        "executeSimpleScanFilterWithToVersionRequest" to ::executeSimpleScanFilterWithToVersionRequest
    )

    private val objects = arrayOf(
        CompleteMarykModel(
            string="haas",
            number = 24u,
            subModel = SimpleMarykModel(
                value = "haha"
            ),
            multi=TypedValue(T2, 22),
            booleanForKey= true,
            dateForKey= LocalDate(2018, 3, 29),
            multiForKey= TypedValue(S1, "hii"),
            enumEmbedded= E1
        )
    )

    override suspend fun initData() {
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

    override suspend fun resetData() {
        dataStore.execute(
            CompleteMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleScanFilterRequest() {
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

    private suspend fun executeSimpleScanFilterWithToVersionRequest() {
        val changeResponse = dataStore.execute(
            CompleteMarykModel.change(
                keys[0].change(
                    Change(string.ref() with "haas2")
                )
            )
        )

        assertType<ChangeSuccess<*>>(changeResponse.statuses[0])

        val scanResponseForLatest = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(
                    string.ref() with "haas"
                )
            )
        )

        expect(0) { scanResponseForLatest.values.size }

        // Only test if all versions are kept
        if (dataStore.keepAllVersions) {
            val scanResponseBeforeChange = dataStore.execute(
                CompleteMarykModel.scan(
                    where = Equals(
                        string.ref() with "haas"
                    ),
                    toVersion = lowestVersion
                )
            )

            expect(1) { scanResponseBeforeChange.values.size }

            scanResponseBeforeChange.values[0].let {
                expect(objects[0]) { it.values }
                expect(keys[0]) { it.key }
            }
        }
    }
}
