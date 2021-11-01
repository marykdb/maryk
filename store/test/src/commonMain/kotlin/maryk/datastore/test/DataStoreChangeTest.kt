package maryk.datastore.test

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.assertRecent
import maryk.datastore.shared.IsDataStore
import maryk.lib.time.Time
import maryk.test.assertType
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import maryk.test.runSuspendingTest
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.expect

class DataStoreChangeTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<TestMarykModel>>()
    private val lastVersions = mutableListOf<ULong>()

    override val allTests = mapOf(
        "executeChangeCheckRequest" to ::executeChangeCheckRequest,
        "executeChangeChangeRequest" to ::executeChangeChangeRequest,
        "executeChangeChangeListItemDoesNotExistRequest" to ::executeChangeChangeListItemDoesNotExistRequest,
        "executeChangeChangeMapDoesNotExistRequest" to ::executeChangeChangeMapDoesNotExistRequest,
        "executeChangeChangeEmbedDoesNotExistRequest" to ::executeChangeChangeEmbedDoesNotExistRequest,
        "executeChangeDeleteRequest" to ::executeChangeDeleteRequest,
        "executeChangeDeleteComplexRequest" to ::executeChangeDeleteComplexRequest,
        "executeChangeDeleteComplexItemsRequest" to ::executeChangeDeleteComplexItemsRequest,
        "executeChangeDeleteFailOnOfTypeRefsItemsRequest" to ::executeChangeDeleteFailOnOfTypeRefsItemsRequest,
        "executeChangeListRequest" to ::executeChangeListRequest,
        "executeChangeSetRequest" to ::executeChangeSetRequest,
        "executeChangeMapRequest" to ::executeChangeMapRequest,
        "executeChangeIncMapRequest" to ::executeChangeIncMapRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                TestMarykModel.add(
                    TestMarykModel(
                        "haha1",
                        5,
                        6u,
                        0.43,
                        LocalDateTime(2018, 3, 2, 0, 0),
                        true,
                        listOfString = listOf("a", "b", "c"),
                        map = mapOf(Time(2, 3, 5) to "test"),
                        set = setOf(LocalDate(2018, 3, 4))
                    ),
                    TestMarykModel(
                        "haha2",
                        3,
                        8u,
                        1.244,
                        LocalDateTime(2018, 1, 2, 0, 0),
                        false,
                        embeddedValues = EmbeddedMarykModel("value"),
                        list = listOf(1, 4, 6),
                        listOfString = listOf("c", "d", "e"),
                        map = mapOf(Time(12, 33, 45) to "another", Time(13, 44, 55) to "another2"),
                        set = setOf(LocalDate(2018, 11, 25), LocalDate(1981, 12, 5)),
                        incMap = mapOf(1u to "a", 2u to "b")
                    ),
                    TestMarykModel(
                        "haha3",
                        6,
                        12u,
                        1333.3,
                        LocalDateTime(2018, 12, 9, 0, 0),
                        false,
                        reference = TestMarykModel.key("AAACKwEAAw")
                    ),
                    TestMarykModel(
                        "haha4",
                        4,
                        14u,
                        1.644,
                        LocalDateTime(2019, 1, 2, 0, 0),
                        false,
                        multi = TypedValue(S1, "string"),
                        listOfString = listOf("f", "g", "h"),
                        map = mapOf(Time(1, 33, 45) to "an other", Time(13, 44, 55) to "an other2"),
                        set = setOf(LocalDate(2015, 11, 25), LocalDate(2001, 12, 5))
                    ),
                    TestMarykModel(
                        "haha5",
                        5,
                        13u,
                        3.44,
                        LocalDateTime(1, 1, 2, 0, 0),
                        true,
                        multi = TypedValue(S1, "v1"),
                        listOfString = listOf("f", "g", "h"),
                        map = mapOf(Time(3, 3, 3) to "three", Time(4, 4, 4) to "4"),
                        set = setOf(LocalDate(2001, 1, 1), LocalDate(2002, 2, 2))
                    ),
                    TestMarykModel("haha6", 1, 13u, 3.44, LocalDateTime(1, 1, 2, 0, 0), false)
                )
            )

            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<TestMarykModel>>(status)
                keys.add(response.key)
                lastVersions.add(response.version)
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lastVersions.clear()
    }

    private fun executeChangeCheckRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    Check(
                        TestMarykModel { string::ref } with "haha1"
                    ),
                    lastVersion = lastVersions[0]
                ),
                keys[0].change(
                    Check(
                        TestMarykModel { string::ref } with "wrong"
                    ),
                    lastVersion = lastVersions[0]
                ),
                keys[0].change(
                    Check(
                        TestMarykModel { string::ref } with "haha1"
                    ),
                    lastVersion = 123uL
                )
            )
        )

        expect(3) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        changeResponse.statuses[1].let { status ->
            val validationFail = assertType<ValidationFail<*>>(status)
            expect(1) { validationFail.exceptions.size }
            assertType<InvalidValueException>(validationFail.exceptions[0])
        }

        changeResponse.statuses[2].let { status ->
            val validationFail = assertType<ValidationFail<*>>(status)
            expect(1) { validationFail.exceptions.size }
            assertType<InvalidValueException>(validationFail.exceptions[0])
        }
    }

    private fun executeChangeChangeRequest() = runSuspendingTest {
        val newIntList = listOf(1, 2, 3)
        val newDateSet = setOf(LocalDate(2019, 1, 19), LocalDate(2019, 1, 18))
        val newValues = EmbeddedMarykModel("Different")

        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { string::ref } with "haha3",
                        TestMarykModel { listOfString refAt 0u } with "z",
                        TestMarykModel { map refAt Time(12, 33, 45) } with "changed",
                        TestMarykModel { list::ref } with newIntList,
                        TestMarykModel { set::ref } with newDateSet,
                        TestMarykModel { embeddedValues::ref } with newValues
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
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().let {
            expect("haha3") { it.values { string } }
            expect("z") { it.values { listOfString }?.get(0) }
            expect("changed") { it.values { map }?.get(Time(12, 33, 45)) }
            expect(newIntList) { it.values { list } }
            expect(newDateSet) { it.values { set } }
            expect(newValues) { it.values { embeddedValues } }
        }
    }

    private fun executeChangeChangeListItemDoesNotExistRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { listOfString refAt 0u } with "z"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        assertType<ServerFail<*>>(changeResponse.statuses[0])
    }

    private fun executeChangeChangeMapDoesNotExistRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { map refAt Time(1, 2, 3) } with "new"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        assertType<ServerFail<*>>(changeResponse.statuses[0])
    }

    private fun executeChangeChangeEmbedDoesNotExistRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { embeddedValues { value::ref } } with "test"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        assertType<ServerFail<*>>(changeResponse.statuses[0])
    }

    private fun executeChangeDeleteRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Delete(TestMarykModel { reference::ref })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[2])
        )

        expect(1) { getResponse.values.size }
        assertNull(getResponse.values.first().values { reference })
    }

    private fun executeChangeDeleteComplexRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[3].change(
                    Delete(TestMarykModel { map::ref }),
                    Delete(TestMarykModel { listOfString::ref }),
                    Delete(TestMarykModel { set::ref }),
                    Delete(TestMarykModel { multi::ref })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[3])
        )

        expect(1) { getResponse.values.size }
        assertNull(getResponse.values.first().values { map })
        assertNull(getResponse.values.first().values { listOfString })
        assertNull(getResponse.values.first().values { set })
        assertNull(getResponse.values.first().values { multi })
    }

    private fun executeChangeDeleteComplexItemsRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[4].change(
                    Delete(TestMarykModel { map refAt Time(3, 3, 3) }),
                    Delete(TestMarykModel { listOfString refAt 1u }),
                    Delete(TestMarykModel { set refAt LocalDate(2001, 1, 1) })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertType<ChangeSuccess<*>>(status)
            assertRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[4])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().values { map }.let {
            assertNotNull(it)
            expect(1) { it.size }
        }
        getResponse.values.first().values { listOfString }.let {
            assertNotNull(it)
            expect(listOf("f", "h")) { it }
            expect(2) { it.size }
        }
        getResponse.values.first().values { set }.let {
            assertNotNull(it)
            expect(1) { it.size }
        }
        getResponse.values.first().values { multi }.let {
            assertNotNull(it)
        }
    }

    private fun executeChangeDeleteFailOnOfTypeRefsItemsRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[4].change(
                    Delete(TestMarykModel { multi refAtType S1 })
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        assertType<ServerFail<*>>(changeResponse.statuses[0])
    }

    private fun executeChangeListRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    Delete(
                        TestMarykModel { listOfString.refAt(1u) }
                    ),
                    ListChange(
                        TestMarykModel { listOfString::ref }.change(
                            deleteValues = listOf("c"),
                            addValuesAtIndex = mapOf(
                                0u to "zero"
                            ),
                            addValuesToEnd = listOf("x", "y", "z")
                        )
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
            TestMarykModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(listOf("zero", "a", "x", "y", "z")) { getResponse.values.first().values { listOfString } }
    }

    private fun executeChangeSetRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Delete(
                        TestMarykModel { set.refAt(LocalDate(2018, 11, 25)) }
                    ),
                    SetChange(
                        TestMarykModel { set::ref }.change(
                            addValues = setOf(LocalDate(2018, 11, 26))
                        )
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
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        expect(setOf(LocalDate(2018, 11, 26), LocalDate(1981, 12, 5))) { getResponse.values.first().values { set } }
    }

    private fun executeChangeMapRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { map.refAt(Time(1, 2, 3)) } with "test1",
                        TestMarykModel { map.refAt(Time(2, 3, 4)) } with "test2"
                    ),
                    Delete(
                        TestMarykModel { map.refAt(Time(12, 33, 45)) }
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
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        expect(
            mapOf(
                Time(13, 44, 55) to "another2",
                Time(1, 2, 3) to "test1",
                Time(2, 3, 4) to "test2"
            )
        ) { getResponse.values.first().values { map } }
    }

    private fun executeChangeIncMapRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { incMap.refAt(1u) } with "newA"
                    ),
                    Delete(
                        TestMarykModel { incMap.refAt(2u) }
                    ),
                    IncMapChange(
                        TestMarykModel { incMap::ref }.change(
                            addValues = listOf("c", "d")
                        )
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            assertType<ChangeSuccess<*>>(status).apply {
                expect(
                    listOf(
                       IncMapAddition(
                           IncMapKeyAdditions(
                               reference = TestMarykModel { incMap::ref },
                               addedKeys = listOf(3u, 4u),
                               addedValues = listOf("c", "d")
                           )
                       )
                    )
                ) { changes }
                assertRecent(version, 1000uL)
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        expect(
            mapOf(
                1u to "newA",
                3u to "c",
                4u to "d"
            )
        ) { getResponse.values.first().values { incMap } }
    }
}
