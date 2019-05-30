package maryk.datastore.memory

import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.test.assertType
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.expect

class InMemoryDataStoreChangeComplexTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<ComplexModel>>()
    private val lastVersions = mutableListOf<ULong>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                ComplexModel.add(
                    ComplexModel(
                        multi = TypedValue(T3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
                        mapStringString = mapOf("a" to "b", "c" to "d")
                    ),
                    ComplexModel(
                        multi = TypedValue(T1, "value"),
                        mapStringString = mapOf("a" to "b", "c" to "d")
                    ),
                    ComplexModel(
                        mapStringString = mapOf("a" to "b", "c" to "d"),
                        mapIntObject = mapOf(1u to EmbeddedMarykModel("v1"), 2u to EmbeddedMarykModel("v2"))
                    ),
                    ComplexModel(
                        mapStringString = mapOf("a" to "b", "c" to "d"),
                        mapIntObject = mapOf(
                            1u to EmbeddedMarykModel("v1", EmbeddedMarykModel("sub")),
                            2u to EmbeddedMarykModel("v2")
                        )
                    ),
                    ComplexModel(
                        mapStringString = mapOf("a" to "b", "c" to "d"),
                        mapIntMulti = mapOf(
                            1u to TypedValue(
                                T3,
                                EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1", EmbeddedMarykModel("sub2")))
                            ),
                            2u to TypedValue(T1, "string"),
                            3u to TypedValue(
                                T3,
                                EmbeddedMarykModel("v2", EmbeddedMarykModel("2sub1", EmbeddedMarykModel("2sub2")))
                            )
                        )
                    ),
                    ComplexModel(
                        multi = TypedValue(T3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
                        mapStringString = mapOf("a" to "b", "c" to "d"),
                        mapIntObject = mapOf(1u to EmbeddedMarykModel("v1"), 2u to EmbeddedMarykModel("v2")),
                        mapIntMulti = mapOf(
                            1u to TypedValue(
                                T3,
                                EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1", EmbeddedMarykModel("sub2")))
                            ),
                            2u to TypedValue(T1, "string"),
                            3u to TypedValue(
                                T3,
                                EmbeddedMarykModel("v2", EmbeddedMarykModel("2sub1", EmbeddedMarykModel("2sub2")))
                            )
                        )
                    )
                )
            )

            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<ComplexModel>>(status)
                keys.add(response.key)
                lastVersions.add(response.version)
            }
        }
    }

    @Test
    fun executeChangeDeleteMultiRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[0].change(
                    Delete(ComplexModel { multi::ref })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }

        assertNull(getResponse.values.first().values { multi })
    }

    @Test
    fun executeChangeDeleteMapRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[2].change(
                    Delete(ComplexModel { mapIntObject::ref })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[2])
        )

        expect(1) { getResponse.values.size }
        assertNull(getResponse.values.first().values { mapIntObject })
    }

    @Test
    fun executeChangeDeleteMapValueRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[2].change(
                    Delete(ComplexModel { mapIntObject refAt 2u })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[2])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { mapIntObject }.let { map ->
            assertNotNull(map)
            expect(1) { map.size }
            assertNull(map[2u])
        }
    }

    @Test
    fun executeChangeDeleteMapSubValueRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[3].change(
                    Delete(ComplexModel { mapIntObject.at(1u) { model::ref } })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[3])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { mapIntObject }.let {
            assertNotNull(it)
            expect(2) { it.size }
            expect(EmbeddedMarykModel("v1")) { it[1u] }
        }
    }

    @Test
    fun executeChangeDeleteMapTypedSubValueRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[4].change(
                    Delete(
                        ComplexModel {
                            mapIntMulti.at(1u) { atType(T3) { model { model::ref } } }
                        },
                        ComplexModel { mapIntMulti.at(3u) { atType(T3) { model::ref } } }
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[4])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { mapIntMulti }.let { mapIntMulti ->
            assertNotNull(mapIntMulti)
            expect(3) { mapIntMulti.size }
            expect(TypedValue(T3, EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1")))) {
                mapIntMulti[1u] as TypedValue<*, *>
            }
            expect(TypedValue(T3, EmbeddedMarykModel("v2"))) {
                mapIntMulti[3u] as TypedValue<*, *>
            }
        }
    }

    @Test
    fun executeChangeChangeValueRequest() = runSuspendingTest {
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
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
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
                    TypedValue(T3, EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1", EmbeddedMarykModel("changed"))))
                ) { mapIntMulti[1u] as TypedValue<*, *> }
            }

            valuesWithMetaData.values { mapIntObject }.let { mapIntObject ->
                assertNotNull(mapIntObject)
                expect(2) { mapIntObject.size }
                expect(EmbeddedMarykModel("mapIntObjectChanged")) { mapIntObject[1u] }
            }

            expect(TypedValue(T3, EmbeddedMarykModel("u3", EmbeddedMarykModel("multi sub changed")))) {
                valuesWithMetaData.values { multi } as TypedValue<*, *>
            }
        }
    }

    @Test
    fun executeChangeChangeReplaceComplexValueRequest() = runSuspendingTest {
        val newMultiValue = TypedValue(T3, EmbeddedMarykModel("a5", EmbeddedMarykModel("ae5")))
        val newMapStringString = mapOf("e" to "f", "g" to "h")
        val newMapIntObject = mapOf(4u to EmbeddedMarykModel("v100"), 8u to EmbeddedMarykModel("v200"))
        val newMapIntMulti = mapOf<UInt, TypedValue<MarykTypeEnum<*>, *>>(
            5u to TypedValue(
                T3,
                EmbeddedMarykModel("v101", EmbeddedMarykModel("suba1", EmbeddedMarykModel("suba2")))
            ),
            10u to TypedValue(T1, "new"),
            3u to TypedValue(T3, EmbeddedMarykModel("v222", EmbeddedMarykModel("2asub1", EmbeddedMarykModel("2asub2"))))
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
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
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
}
