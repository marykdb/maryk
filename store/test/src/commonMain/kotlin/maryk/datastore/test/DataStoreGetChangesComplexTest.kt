package maryk.datastore.test

import maryk.core.models.fromChanges
import maryk.core.properties.enum.invoke
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.assertFalse
import kotlin.test.expect

class DataStoreGetChangesComplexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<ComplexModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleGetChangesRequest" to ::executeSimpleGetChangesRequest
    )

    private val initialValues = ComplexModel.create {
        multi with T3 {
            value with "u3"
            model with {
                value with "ue3"
            }
        }
        mapStringString with mapOf("a" to "b", "c" to "d")
        mapIntObject with mapOf(
            1u to EmbeddedMarykModel.create { value with "v1" },
            2u to EmbeddedMarykModel.create { value with "v2" }
        )
        mapIntMulti with mapOf(
            1u to T3 {
                value with "v1"
                model with {
                    value with "sub1"
                    model with {
                        value with "sub2"
                    }
                }
            },
            2u to T1("string"),
            3u to T3 {
                value with "v2"
                model with {
                    value with "2sub1"
                    model with {
                        value with "2sub2"
                    }
                }
            }
        )
    }

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            ComplexModel.add(
                initialValues
            )
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<ComplexModel>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            ComplexModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleGetChangesRequest() {
        val getResponse = dataStore.execute(
            ComplexModel.getChanges(*keys.toTypedArray())
        )

        expect(1) { getResponse.changes.size }

        expect(FetchByKey) {getResponse.dataFetchType}

        val exported = getResponse.changes.single()
        expect(keys.single()) { exported.key }
        val version = exported.changes.single()
        expect(lowestVersion) { version.version }
        expect(ObjectCreate) { version.changes.first() }
        expect(initialValues) { ComplexModel.fromChanges(null, version.changes) }
        assertFalse(version.changes.filterIsInstance<Change>().any { change ->
            change.referenceValuePairs.any { it.value == Unit }
        })

    }
}
