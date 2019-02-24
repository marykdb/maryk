package maryk.datastore.memory

import maryk.core.properties.definitions.wrapper.atKeyAndType
import maryk.core.properties.definitions.wrapper.refAtKey
import maryk.core.properties.definitions.wrapper.refAtKeyAndType
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
import maryk.core.query.responses.statuses.Success
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.EmbeddedMarykModel.Properties
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldNotBe
import kotlin.test.Test

class InMemoryDataStoreChangeComplexTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<ComplexModel>>()
    private val lastVersions = mutableListOf<ULong>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                ComplexModel.add(
                    ComplexModel(
                        multi = TypedValue(V3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
                        mapStringString = mapOf("a" to "b", "c" to "d")
                    ),
                    ComplexModel(
                        multi = TypedValue(V1, "value"),
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
                                V3,
                                EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1", EmbeddedMarykModel("sub2")))
                            ),
                            2u to TypedValue(V1, "string"),
                            3u to TypedValue(
                                V3,
                                EmbeddedMarykModel("v2", EmbeddedMarykModel("2sub1", EmbeddedMarykModel("2sub2")))
                            )
                        )
                    ),
                    ComplexModel(
                        multi = TypedValue(V3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
                        mapStringString = mapOf("a" to "b", "c" to "d"),
                        mapIntObject = mapOf(1u to EmbeddedMarykModel("v1"), 2u to EmbeddedMarykModel("v2")),
                        mapIntMulti = mapOf(
                            1u to TypedValue(
                                V3,
                                EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1", EmbeddedMarykModel("sub2")))
                            ),
                            2u to TypedValue(V1, "string"),
                            3u to TypedValue(
                                V3,
                                EmbeddedMarykModel("v2", EmbeddedMarykModel("2sub1", EmbeddedMarykModel("2sub2")))
                            )
                        )
                    )
                )
            )

            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<ComplexModel>>(status)
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
                    Delete(ComplexModel.ref { multi })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[0])
        )

        getResponse.values.size shouldBe 1

        getResponse.values.first().values { multi } shouldBe null
    }

    @Test
    fun executeChangeDeleteMapRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[2].change(
                    Delete(ComplexModel.ref { mapIntObject })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[2])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { mapIntObject } shouldBe null
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

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[2])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { mapIntObject }.let {
            it shouldNotBe null
            it?.size shouldBe 1
            it?.get(2u) shouldBe null
        }
    }

    @Test
    fun executeChangeDeleteMapSubValueRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[3].change(
                    Delete(ComplexModel { mapIntObject.refAtKey(1u) { model } })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[3])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { mapIntObject }.let {
            it shouldNotBe null
            it?.size shouldBe 2
            it?.get(1u) shouldBe EmbeddedMarykModel("v1")
        }
    }

    @Test
    fun executeChangeDeleteMapTypedSubValueRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[4].change(
                    Delete(
                        ComplexModel {
                            mapIntMulti.atKeyAndType(
                                1u,
                                V3,
                                EmbeddedMarykModel.Properties
                            ) { model ref { model } }
                        },
                        ComplexModel { mapIntMulti.refAtKeyAndType(3u, V3, EmbeddedMarykModel.Properties) { model } }
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[4])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { mapIntMulti }.let {
            it shouldNotBe null
            it?.size shouldBe 3
            it?.get(1u) shouldBe TypedValue(V3, EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1")))
            it?.get(3u) shouldBe TypedValue(V3, EmbeddedMarykModel("v2"))
        }
    }

    @Test
    fun executeChangeChangeValueRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[5].change(
                    Change(
                        ComplexModel {
                            mapIntMulti.atKeyAndType(
                                1u,
                                V3,
                                Properties
                            ) { model { model ref { value } } }
                        } with "changed",
                        ComplexModel { mapIntObject.refAtKey(1u) { value } } with "mapIntObjectChanged",
                        ComplexModel { multi.withType(V3, Properties) { model ref { value } } } with "multi sub changed"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[5])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().let { valuesWithMetaData ->
            valuesWithMetaData.values { mapIntMulti }.let {
                it shouldNotBe null
                it?.size shouldBe 3
                it?.get(1u) shouldBe TypedValue(
                    V3,
                    EmbeddedMarykModel("v1", EmbeddedMarykModel("sub1", EmbeddedMarykModel("changed")))
                )
            }

            valuesWithMetaData.values { mapIntObject }.let {
                it shouldNotBe null
                it?.size shouldBe 2
                it?.get(1u) shouldBe EmbeddedMarykModel("mapIntObjectChanged")
            }

            valuesWithMetaData.values { multi }.let {
                it shouldBe TypedValue(V3, EmbeddedMarykModel("u3", EmbeddedMarykModel("multi sub changed")))
            }
        }
    }

    @Test
    fun executeChangeChangeReplaceComplexValueRequest() = runSuspendingTest {
        val newMultiValue = TypedValue(V3, EmbeddedMarykModel("a5", EmbeddedMarykModel("ae5")))
        val newMapStringString = mapOf("e" to "f", "g" to "h")
        val newMapIntObject = mapOf(4u to EmbeddedMarykModel("v100"), 8u to EmbeddedMarykModel("v200"))
        val newMapIntMulti = mapOf(
            5u to TypedValue(
                V3,
                EmbeddedMarykModel("v101", EmbeddedMarykModel("suba1", EmbeddedMarykModel("suba2")))
            ),
            10u to TypedValue(V1, "new"),
            3u to TypedValue(V3, EmbeddedMarykModel("v222", EmbeddedMarykModel("2asub1", EmbeddedMarykModel("2asub2"))))
        )

        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[5].change(
                    Change(
                        ComplexModel.ref { multi } with newMultiValue,
                        ComplexModel.ref { mapStringString } with newMapStringString,
                        ComplexModel.ref { mapIntObject } with newMapIntObject,
                        ComplexModel.ref { mapIntMulti } with newMapIntMulti
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[5])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().let { valuesWithMetaData ->
            valuesWithMetaData.values { multi } shouldBe newMultiValue
            valuesWithMetaData.values { mapStringString } shouldBe newMapStringString
            valuesWithMetaData.values { mapIntObject } shouldBe newMapIntObject
            valuesWithMetaData.values { mapIntMulti } shouldBe newMapIntMulti
        }
    }
}
