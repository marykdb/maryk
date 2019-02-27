package maryk.datastore.memory

import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.Date
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V1
import maryk.test.models.TestMarykModel
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldNotBe
import kotlin.test.Test

class InMemoryDataStoreChangeTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<TestMarykModel>>()
    private val lastVersions = mutableListOf<ULong>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                TestMarykModel.add(
                    TestMarykModel(
                        "haha1",
                        5,
                        6u,
                        0.43,
                        DateTime(2018, 3, 2),
                        true,
                        listOfString = listOf("a", "b", "c"),
                        map = mapOf(Time(2, 3, 5) to "test"),
                        set = setOf(Date(2018, 3, 4))
                    ),
                    TestMarykModel(
                        "haha2",
                        3,
                        8u,
                        1.244,
                        DateTime(2018, 1, 2),
                        false,
                        embeddedValues = EmbeddedMarykModel("value"),
                        list = listOf(1, 4, 6),
                        listOfString = listOf("c", "d", "e"),
                        map = mapOf(Time(12, 33, 45) to "another", Time(13, 44, 55) to "another2"),
                        set = setOf(Date(2018, 11, 25), Date(1981, 12, 5))
                    ),
                    TestMarykModel(
                        "haha3",
                        6,
                        12u,
                        1333.3,
                        DateTime(2018, 12, 9),
                        false,
                        reference = TestMarykModel.key("AAACKwEBAQAC")
                    ),
                    TestMarykModel(
                        "haha4",
                        4,
                        14u,
                        1.644,
                        DateTime(2019, 1, 2),
                        false,
                        multi = TypedValue(V1, "string"),
                        listOfString = listOf("f", "g", "h"),
                        map = mapOf(Time(1, 33, 45) to "an other", Time(13, 44, 55) to "an other2"),
                        set = setOf(Date(2015, 11, 25), Date(2001, 12, 5))
                    ),
                    TestMarykModel(
                        "haha5",
                        5,
                        13u,
                        3.44,
                        DateTime(1, 1, 2),
                        true,
                        multi = TypedValue(V1, "v1"),
                        listOfString = listOf("f", "g", "h"),
                        map = mapOf(Time(3, 3, 3) to "three", Time(4, 4, 4) to "4"),
                        set = setOf(Date(2001, 1, 1), Date(2002, 2, 2))
                    ),
                    TestMarykModel("haha6", 1, 13u, 3.44, DateTime(1, 1, 2), false)
                )
            )

            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<TestMarykModel>>(status)
                keys.add(response.key)
                lastVersions.add(response.version)
            }
        }
    }

    @Test
    fun executeChangeCheckRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    Check(
                        TestMarykModel.ref { string } with "haha1"
                    ),
                    lastVersion = lastVersions[0]
                ),
                keys[0].change(
                    Check(
                        TestMarykModel.ref { string } with "wrong"
                    ),
                    lastVersion = lastVersions[0]
                ),
                keys[0].change(
                    Check(
                        TestMarykModel.ref { string } with "haha1"
                    ),
                    lastVersion = 123uL
                )
            )
        )

        changeResponse.statuses.size shouldBe 3
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        changeResponse.statuses[1].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.size shouldBe 1
            shouldBeOfType<InvalidValueException>(validationFail.exceptions[0])
        }

        changeResponse.statuses[2].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.size shouldBe 1
            shouldBeOfType<InvalidValueException>(validationFail.exceptions[0])
        }
    }

    @Test
    fun executeChangeChangeRequest() = runSuspendingTest {
        val newIntList = listOf(1, 2, 3)
        val newDateSet = setOf(Date(2019, 1, 19), Date(2019, 1, 18))
        val newValues = EmbeddedMarykModel("Different")

        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel.ref { string } with "haha3",
                        TestMarykModel { listOfString refAt 0u } with "z",
                        TestMarykModel { map refAt Time(12, 33, 45) } with "changed",
                        TestMarykModel.ref { list } with newIntList,
                        TestMarykModel.ref { set } with newDateSet,
                        TestMarykModel.ref { embeddedValues } with newValues
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        @Suppress("UNUSED_VARIABLE") val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().let {
            it.values { string } shouldBe "haha3"
            it.values { listOfString }?.get(0) shouldBe "z"
            it.values { map }?.get(Time(12, 33, 45)) shouldBe "changed"
            it.values { list } shouldBe newIntList
            it.values { set } shouldBe newDateSet
            it.values { embeddedValues } shouldBe newValues
        }
    }

    @Test
    fun executeChangeChangeListItemDoesNotExistRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { listOfString refAt 0u } with "z"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        shouldBeOfType<ServerFail<*>>(changeResponse.statuses[0])
    }

    @Test
    fun executeChangeChangeMapDoesNotExistRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { map refAt Time(1, 2, 3) } with "new"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        shouldBeOfType<ServerFail<*>>(changeResponse.statuses[0])
    }

    @Test
    fun executeChangeChangeEmbedDoesNotExistRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { embeddedValues.ref { value } } with "test"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        shouldBeOfType<ServerFail<*>>(changeResponse.statuses[0])
    }

    @Test
    fun executeChangeDeleteRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Delete(TestMarykModel.ref { reference })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[2])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { reference } shouldBe null
    }

    @Test
    fun executeChangeDeleteComplexRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[3].change(
                    Delete(TestMarykModel.ref { map }),
                    Delete(TestMarykModel.ref { listOfString }),
                    Delete(TestMarykModel.ref { set }),
                    Delete(TestMarykModel.ref { multi })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[3])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { map } shouldBe null
        getResponse.values.first().values { listOfString } shouldBe null
        getResponse.values.first().values { set } shouldBe null
        getResponse.values.first().values { multi } shouldBe null
    }

    @Test
    fun executeChangeDeleteComplexItemsRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[4].change(
                    Delete(TestMarykModel { map refAt Time(3, 3, 3) }),
                    Delete(TestMarykModel { listOfString refAt 1u }),
                    Delete(TestMarykModel { set refAt Date(2001, 1, 1) })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[4])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { map }.let {
            it shouldNotBe null
            it?.size shouldBe 1
        }
        getResponse.values.first().values { listOfString }.let {
            it shouldNotBe null
            it!! shouldBe listOf("f", "h")
            it.size shouldBe 2
        }
        getResponse.values.first().values { set }.let {
            it shouldNotBe null
            it?.size shouldBe 1
        }
        getResponse.values.first().values { multi }.let {
            it shouldNotBe null
        }
    }

    @Test
    fun executeChangeDeleteFailOnOfTypeRefsItemsRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[4].change(
                    Delete(TestMarykModel { multi refAtType V1 })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        shouldBeOfType<ServerFail<*>>(changeResponse.statuses[0])
    }

    @Test
    fun executeChangeListRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    Delete(
                        TestMarykModel { listOfString.refAt(1u) }
                    ),
                    ListChange(
                        TestMarykModel.ref { listOfString }.change(
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

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[0])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { listOfString } shouldBe listOf("zero", "a", "x", "y", "z")
    }

    @Test
    fun executeChangeSetRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Delete(
                        TestMarykModel { set.refAt(Date(2018, 11, 25)) }
                    ),
                    SetChange(
                        TestMarykModel.ref { set }.change(
                            addValues = setOf(Date(2018, 11, 26))
                        )
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
            TestMarykModel.get(keys[1])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { set } shouldBe setOf(Date(2018, 11, 26), Date(1981, 12, 5))
    }

    @Test
    fun executeChangeMapRequest() = runSuspendingTest {
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

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { map } shouldBe mapOf(
            Time(13, 44, 55) to "another2",
            Time(1, 2, 3) to "test1",
            Time(2, 3, 4) to "test2"
        )
    }
}
