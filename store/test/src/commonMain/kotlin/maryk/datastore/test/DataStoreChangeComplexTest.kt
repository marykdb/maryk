package maryk.datastore.test

import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.EmbeddedMarykModel.model
import maryk.test.models.EmbeddedMarykModel.value
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.expect

class DataStoreChangeComplexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<ComplexModel>>()
    private val lastVersions = mutableListOf<ULong>()

    override val allTests = mapOf(
        "executeChangeDeleteMultiRequest" to ::executeChangeDeleteMultiRequest,
        "executeChangeDeleteMapRequest" to ::executeChangeDeleteMapRequest,
        "executeChangeDeleteMapValueRequest" to ::executeChangeDeleteMapValueRequest,
        "executeChangeDeleteMapSubValueRequest" to ::executeChangeDeleteMapSubValueRequest,
        "executeChangeDeleteMapTypedSubValueRequest" to ::executeChangeDeleteMapTypedSubValueRequest,
        "executeChangeChangeValueRequest" to ::executeChangeChangeValueRequest,
        "executeChangeInsertValueRequest" to ::executeChangeInsertValueRequest,
        "executeChangeChangeReplaceComplexValueRequest" to ::executeChangeChangeReplaceComplexValueRequest,
        "executeChangeIncMapRequest" to ::executeChangeIncMapRequest
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            ComplexModel.add(
                ComplexModel.create {
                    multi with TypedValue(
                        T3,
                        EmbeddedMarykModel.create {
                            value with "u3"
                            model with EmbeddedMarykModel.create { value with "ue3" }
                        }
                    )
                    mapStringString with mapOf("a" to "b", "c" to "d")
                    incMap with mapOf(
                        1u to EmbeddedMarykModel.create { value with "o" },
                        2u to EmbeddedMarykModel.create { value with "p" },
                    )
                },
                ComplexModel.create {
                    multi with TypedValue(T1, "value")
                    mapStringString with mapOf("a" to "b", "c" to "d")
                },
                ComplexModel.create {
                    mapStringString with mapOf("a" to "b", "c" to "d")
                    mapIntObject with mapOf(
                        1u to EmbeddedMarykModel.create { value with "v1" },
                        2u to EmbeddedMarykModel.create { value with "v2" },
                    )
                },
                ComplexModel.create {
                    mapStringString with mapOf("a" to "b", "c" to "d")
                    mapIntObject with mapOf(
                        1u to EmbeddedMarykModel.create {
                            value with "v1"
                            model with EmbeddedMarykModel.create { value with "sub" }
                        },
                        2u to EmbeddedMarykModel.create { value with "v2" }
                    )
                },
                ComplexModel.create {
                    mapStringString with mapOf("a" to "b", "c" to "d")
                    mapIntMulti with mapOf(
                        1u to TypedValue(
                            T3,
                            EmbeddedMarykModel.create { value with "v1"; model with EmbeddedMarykModel.create { value with "sub1"; model with EmbeddedMarykModel.create { value with "sub2" } } }
                        ),
                        2u to TypedValue(T1, "string"),
                        3u to TypedValue(
                            T3,
                            EmbeddedMarykModel.create { value with "v2"; model with EmbeddedMarykModel.create { value with "2sub1"; model with EmbeddedMarykModel.create { value with "2sub2" } } }
                        )
                    )
                },
                ComplexModel.create {
                    multi with TypedValue(T3, EmbeddedMarykModel.create { value with "u3"; model with EmbeddedMarykModel.create { value with "ue3" } })
                    mapStringString with mapOf("a" to "b", "c" to "d")
                    mapIntObject with mapOf(1u to EmbeddedMarykModel.create { value with "v1" }, 2u to EmbeddedMarykModel.create { value with "v2" })
                    mapIntMulti with mapOf(
                        1u to TypedValue(
                            T3,
                            EmbeddedMarykModel.create { value with "v1"; model with EmbeddedMarykModel.create { value with "sub1"; model with EmbeddedMarykModel.create { value with "sub2" } } }
                        ),
                        2u to TypedValue(T1, "string"),
                        3u to TypedValue(
                            T3,
                            EmbeddedMarykModel.create { value with "v2"; model with EmbeddedMarykModel.create { value with "2sub1"; model with EmbeddedMarykModel.create { value with "2sub2" } } }
                        )
                    )
                },
                ComplexModel.create {
                    mapIntObject with mapOf(
                        1u to EmbeddedMarykModel.create { value with "v1" },
                        2u to EmbeddedMarykModel.create { value with "v2" }
                    )
                },
                ComplexModel.create {
                    mapStringString with mapOf("a" to "b", "c" to "d")
                }
            )
        )

        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<ComplexModel>>(status)
            keys.add(response.key)
            lastVersions.add(response.version)
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            ComplexModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lastVersions.clear()
    }

    private suspend fun executeChangeDeleteMultiRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[0].change(
                    Change(ComplexModel { multi::ref } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }

        assertNull(getResponse.values.first().values { multi })
    }

    private suspend fun executeChangeDeleteMapRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[2].change(
                    Change(ComplexModel { mapIntObject::ref } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[2])
        )

        expect(1) { getResponse.values.size }
        assertNull(getResponse.values.first().values { mapIntObject })
    }

    private suspend fun executeChangeDeleteMapValueRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[6].change(
                    Change(ComplexModel { mapIntObject refAt 2u } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[6])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { mapIntObject }.let { map ->
            assertNotNull(map)
            expect(1) { map.size }
            assertNull(map[2u])
        }
    }

    private suspend fun executeChangeDeleteMapSubValueRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[3].change(
                    Change(ComplexModel { mapIntObject.at(1u) { model::ref } } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[3])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { mapIntObject }.let {
            assertNotNull(it)
            expect(2) { it.size }
            expect(EmbeddedMarykModel.create { value with "v1" }) { it[1u] }
        }
    }

    private suspend fun executeChangeDeleteMapTypedSubValueRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[4].change(
                    Change(
                        ComplexModel {
                            mapIntMulti.at(1u) { atType(T3) { model { model::ref } } }
                        } with null,
                        ComplexModel { mapIntMulti.at(3u) { atType(T3) { model::ref } } } with null
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[4])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { mapIntMulti }.let { mapIntMulti ->
            assertNotNull(mapIntMulti)
            expect(3) { mapIntMulti.size }
            expect(TypedValue(T3, EmbeddedMarykModel.create { value with "v1"; model with EmbeddedMarykModel.create { value with "sub1" } })) {
                mapIntMulti[1u] as TypedValue<*, *>
            }
            expect(TypedValue(T3, EmbeddedMarykModel.create { value with "v2" })) {
                mapIntMulti[3u] as TypedValue<*, *>
            }
        }
    }

    private suspend fun executeChangeChangeValueRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[5].change(
                    Change(
                        ComplexModel {
                            mapIntMulti.at(1u) { atType(T3) { model { model { value::ref } } } }
                        } with "changed",
                        ComplexModel { mapIntObject.at(1u) { value::ref } } with "mapIntObjectChanged",
                        ComplexModel { multi.withType(T3) { model { value::ref } } } with "multi sub changed"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[5])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().let { valuesWithMetaData ->
            valuesWithMetaData.values { mapIntMulti }.let { mapIntMulti ->
                assertNotNull(mapIntMulti)
                expect(3) { mapIntMulti.size }
                expect(
                    TypedValue(T3, EmbeddedMarykModel.create { value with "v1"; model with EmbeddedMarykModel.create { value with "sub1"; model with EmbeddedMarykModel.create { value with "changed" } } })
                ) { mapIntMulti[1u] as TypedValue<*, *> }
            }

            valuesWithMetaData.values { mapIntObject }.let { mapIntObject ->
                assertNotNull(mapIntObject)
                expect(2) { mapIntObject.size }
                expect(EmbeddedMarykModel.create { value with "mapIntObjectChanged" }) { mapIntObject[1u] }
            }

            expect(TypedValue(T3, EmbeddedMarykModel.create { value with "u3"; model with EmbeddedMarykModel.create { value with "multi sub changed" } })) {
                valuesWithMetaData.values { multi } as TypedValue<*, *>
            }
        }
    }

    private suspend fun executeChangeInsertValueRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[7].change(
                    Change(
                        ComplexModel { mapIntObject refAt 5u } with EmbeddedMarykModel.create { value with "v5" },
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[7])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().let { valuesWithMetaData ->
            valuesWithMetaData.values { mapIntObject }.let { mapIntObject ->
                assertNotNull(mapIntObject)
                expect(1) { mapIntObject.size }
                expect(EmbeddedMarykModel.create { value with "v5" }) { mapIntObject[5u] }
            }
        }
    }

    private suspend fun executeChangeChangeReplaceComplexValueRequest() {
        val newMultiValue = TypedValue(T3, EmbeddedMarykModel.create { value with "a5"; model with EmbeddedMarykModel.create { value with "ae5" } })
        val newMapStringString = mapOf("e" to "f", "g" to "h")
        val newMapIntObject = mapOf(4u to EmbeddedMarykModel.create { value with "v100" }, 8u to EmbeddedMarykModel.create { value with "v200" })
        val newMapIntMulti = mapOf<UInt, TypedValue<MarykTypeEnum<*>, *>>(
            5u to TypedValue(
                T3,
                EmbeddedMarykModel.create { value with "v101"; model with EmbeddedMarykModel.create { value with "suba1"; model with EmbeddedMarykModel.create { value with "suba2" } } }
            ),
            10u to TypedValue(T1, "new"),
            3u to TypedValue(T3, EmbeddedMarykModel.create { value with "v222"; model with EmbeddedMarykModel.create { value with "2asub1"; model with EmbeddedMarykModel.create { value with "2asub2" } } })
        )

        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[5].change(
                    Change(
                        ComplexModel { multi::ref } with newMultiValue,
                        ComplexModel { mapStringString::ref } with newMapStringString,
                        ComplexModel { mapIntObject::ref } with newMapIntObject,
                        ComplexModel { mapIntMulti::ref } with newMapIntMulti
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[5])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().let { valuesWithMetaData ->
            expect(newMultiValue) { valuesWithMetaData.values { multi } as TypedValue<*, *> }
            expect(newMapStringString) { valuesWithMetaData.values { mapStringString } }
            expect(newMapIntObject) { valuesWithMetaData.values { mapIntObject } }
            expect(newMapIntMulti) { valuesWithMetaData.values { mapIntMulti } }
        }
    }

    private suspend fun executeChangeIncMapRequest() {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[0].change(
                    Change(
                        ComplexModel { incMap.refAt(1u) } with EmbeddedMarykModel.create { value with "n" }
                    ),
                    Change(
                        ComplexModel { incMap.refAt(2u) } with null
                    ),
                    IncMapChange(
                        ComplexModel { incMap::ref }.change(
                            addValues = listOf(
                                EmbeddedMarykModel.create { value with "q" },
                                EmbeddedMarykModel.create { value with "r" }
                            )
                        )
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            assertStatusIs<ChangeSuccess<*>>(status).apply {
                expect(
                    listOf(
                        IncMapAddition(
                            IncMapKeyAdditions(
                                ComplexModel { incMap::ref },
                                listOf(3u, 4u),
                                listOf(
                                    EmbeddedMarykModel.create { value with "q" },
                                    EmbeddedMarykModel.create { value with "r" }
                                )
                            )
                        )
                    )
                ) { changes }
                assertRecent(version, 7000uL)
            }
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(
            mapOf(
                1u to EmbeddedMarykModel.create { value with "n" },
                3u to EmbeddedMarykModel.create { value with "q" },
                4u to EmbeddedMarykModel.create { value with "r" }
            )
        ) { getResponse.values.first().values { incMap } }
    }
}
