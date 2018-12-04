@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory

import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.Date
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MapChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.TestMarykModel
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
class InMemoryDataStoreChangeTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<TestMarykModel>>()
    private val lastVersions = mutableListOf<ULong>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                TestMarykModel.add(
                    TestMarykModel("haha1", 5, 6u, 0.43, DateTime(2018, 3, 2), true, listOfString = listOf("a", "b", "c"), map = mapOf(Time(2, 3, 5) to "test"), set = setOf(Date(2018, 3, 4))),
                    TestMarykModel("haha2", 3, 8u, 1.244, DateTime(2018, 1, 2), false, listOfString = listOf("c", "d", "e"), map = mapOf(Time(12, 33, 45) to "another", Time(13, 44, 55) to "another2"), set = setOf(Date(2018, 11, 25), Date(1981, 12, 5))),
                    TestMarykModel("haha3", 6, 12u, 1333.3, DateTime(2018, 12, 9), false, reference = TestMarykModel.key("AAACKwEBAQAC"))
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
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel.ref { string } with "haha3"
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
        getResponse.values.first().values { string } shouldBe "haha3"
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
    fun executeChangeListRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    ListChange(
                        TestMarykModel.ref { listOfString }.change(
                            deleteAtIndex = setOf(1),
                            deleteValues = listOf("c"),
                            addValuesAtIndex = mapOf(
                                0 to "zero"
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
                    SetChange(
                        TestMarykModel.ref { set }.change(
                            deleteValues = setOf(Date(2018, 11, 25)),
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
                    MapChange(
                        TestMarykModel.ref { map }.change(
                            keysToDelete = setOf(Time(12, 33, 45)),
                            valuesToAdd = mapOf(
                                Time(1, 2, 3) to "test1",
                                Time(2, 3, 4) to "test2"
                            )
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
        getResponse.values.first().values { map } shouldBe mapOf(
            Time(13, 44, 55) to "another2",
            Time(1, 2, 3) to "test1",
            Time(2, 3, 4) to "test2"
        )
    }
}
