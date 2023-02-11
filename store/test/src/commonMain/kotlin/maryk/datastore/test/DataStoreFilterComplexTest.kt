package maryk.datastore.test

import maryk.core.clock.HLC
import maryk.core.models.PropertyBaseRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.IsFilter
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.ComplexModel
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataStoreFilterComplexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<PropertyBaseRootDataModel<ComplexModel>>>()
    private val lastVersions = mutableListOf<ULong>()
    private lateinit var firstKey: Key<PropertyBaseRootDataModel<ComplexModel>>

    override val allTests = mapOf(
        "doExistsFilter" to ::doExistsFilter,
        "doEqualsFilter" to ::doEqualsFilter
    )

    private val dataObject = ComplexModel(
        mapStringString = mapOf(
            "k1" to "v1",
            "k2" to "v2"
        )
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            ComplexModel.add(
                dataObject
            )
        )

        addResponse.statuses.forEach { status ->
            val response = assertIs<AddSuccess<PropertyBaseRootDataModel<ComplexModel>>>(status)
            keys.add(response.key)
            lastVersions.add(response.version)
        }

        firstKey = keys[0]
    }

    override suspend fun resetData() {
        dataStore.execute(
            ComplexModel.Model.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
        lastVersions.clear()
    }

    private suspend fun filterMatches(filter: IsFilter, hlc: HLC? = null): Boolean {
        val response = dataStore.execute(
            ComplexModel.get(
                firstKey,
                where = filter,
                toVersion = hlc?.timestamp
            )
        )
        return response.values.isNotEmpty()
    }

    private suspend fun doExistsFilter() {
        assertTrue {
            filterMatches(
                Exists(ComplexModel { mapStringString.refAt("k1") })
            )
        }

        if (dataStore.keepAllVersions) {
            // Below version it did not exist
            assertFalse {
                filterMatches(
                    Exists(ComplexModel { mapStringString.refAt("k1") }),
                    HLC(lastVersions.first() - 1u)
                )
            }

            // With higher version it should be found
            assertTrue {
                filterMatches(
                    Exists(ComplexModel { mapStringString.refAt("k1") }),
                    HLC(lastVersions.first() + 1u)
                )
            }
        }
    }

    private suspend fun doEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(ComplexModel { mapStringString.refAt("k1") } with "v1")
            )
        }

        if (dataStore.keepAllVersions) {
            assertFalse {
                filterMatches(
                    Equals(ComplexModel { mapStringString.refAt("k1") } with "v1"),
                    HLC(lastVersions.last() - 1u)
                )
            }

            // With higher version it should be found
            assertTrue {
                filterMatches(
                    Equals(ComplexModel { mapStringString.refAt("k1") } with "v1"),
                    HLC(lastVersions.first() + 1u)
                )
            }
        }

        assertTrue {
            filterMatches(
                Equals(ComplexModel { mapStringString.refToAny() } with "v2")
            )
        }
    }
}
