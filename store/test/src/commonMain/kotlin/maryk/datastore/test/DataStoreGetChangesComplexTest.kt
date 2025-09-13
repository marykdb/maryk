package maryk.datastore.test

import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.pairs.withType
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
import kotlin.test.expect

class DataStoreGetChangesComplexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<ComplexModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleGetChangesRequest" to ::executeSimpleGetChangesRequest
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            ComplexModel.add(
                ComplexModel.create {
                    multi with TypedValue(T3, EmbeddedMarykModel.create {
                        value with "u3"
                        model with {
                            value with "ue3"
                        }
                    })
                    mapStringString with mapOf("a" to "b", "c" to "d")
                    mapIntObject with mapOf(
                        1u to EmbeddedMarykModel.create { value with "v1" },
                        2u to EmbeddedMarykModel.create { value with "v2" }
                    )
                    mapIntMulti with mapOf(
                        1u to TypedValue(T3,
                            EmbeddedMarykModel.create {
                                value with "v1"
                                model with {
                                    value with "sub1"
                                    model with {
                                        value with "sub2"
                                    }
                                }
                            }
                        ),
                        2u to TypedValue(T1, "string"),
                        3u to TypedValue(T3,
                            EmbeddedMarykModel.create {
                                value with "v2"
                                model with {
                                    value with "2sub1"
                                    model with {
                                        value with "2sub2"
                                    }
                                }
                            }
                        )
                    )
                }
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

        expect(
            listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    ObjectCreate,
                    MultiTypeChange(
                        ComplexModel { multi::ref } withType T3,
                        ComplexModel { mapIntMulti.refAt(1u) } withType T3,
                        ComplexModel { mapIntMulti.refAt(3u) } withType T3
                    ),
                    Change(
                        ComplexModel { multi.withType(T3) { value::ref } } with "u3",
                        ComplexModel { multi.withType(T3) { model { value::ref } } } with "ue3",
                        ComplexModel { mapStringString refAt "a" } with "b",
                        ComplexModel { mapStringString refAt "c" } with "d",
                        ComplexModel { mapIntObject refAt 1u } with Unit,
                        ComplexModel { mapIntObject.at(1u) { value::ref } } with "v1",
                        ComplexModel { mapIntObject refAt 2u } with Unit,
                        ComplexModel { mapIntObject.at(2u) { value::ref } } with "v2",
                        ComplexModel { mapIntMulti.at(1u) { atType(T3) { value::ref } } } with "v1",
                        ComplexModel { mapIntMulti.at(1u) { atType(T3) { model { value::ref } } } } with "sub1",
                        ComplexModel { mapIntMulti.at(1u) { atType(T3) { model { model { value::ref } } } } } with "sub2",
                        ComplexModel { mapIntMulti refAt 2u } with TypedValue(T1, "string"),
                        ComplexModel { mapIntMulti.at(3u) { atType(T3) { value::ref } } } with "v2",
                        ComplexModel { mapIntMulti.at(3u) { atType(T3) { model { value::ref } } } } with "2sub1",
                        ComplexModel { mapIntMulti.at(3u) { atType(T3) { model { model { value::ref } } } } } with "2sub2"
                    )
                ))
            )
        ) {
            getResponse.changes[0].changes
        }
    }
}
