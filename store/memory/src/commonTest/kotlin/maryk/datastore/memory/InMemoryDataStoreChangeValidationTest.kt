package maryk.datastore.memory

import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.types.Date
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.TestMarykModel
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class InMemoryDataStoreChangeValidationTest {
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
                        listOfString = listOf("c"),
                        reference = TestMarykModel.key("AAACKwEAAw")
                    )
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
    fun executeChangeChangeWithValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { string::ref } with "wrong"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<InvalidValueException>(first())
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        // Should not have changes
        getResponse.values.size shouldBe 1
        getResponse.values.first().values { string } shouldBe "haha2"
    }

    @Test
    fun executeChangeDeleteWithValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Delete(TestMarykModel { uint::ref })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<AlreadySetException>(first())
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[2])
        )

        // Should stay the same
        getResponse.values.size shouldBe 1
        getResponse.values.first().values { uint } shouldBe 12u
    }

    @Test
    fun executeChangeListWitTooManyItemsValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    ListChange(
                        TestMarykModel { listOfString::ref }.change(
                            addValuesToEnd = listOf("u", "v", "w", "x", "y", "z")
                        )
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<TooManyItemsException>(first()).apply {
                    size shouldBe 9u
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[0])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { listOfString } shouldBe listOf("a", "b", "c")
    }

    @Test
    fun executeChangeListWithContentValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    ListChange(
                        TestMarykModel { listOfString::ref }.change(
                            addValuesAtIndex = mapOf(
                                0u to "verylongwrongvalue"
                            )
                        )
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<InvalidSizeException>(first()).apply {
                    reference shouldBe TestMarykModel { listOfString refAt 0u }
                    value shouldBe "verylongwrongvalue"
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[0])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { listOfString } shouldBe listOf("a", "b", "c")
    }

    @Test
    fun executeChangeSetWithMaxSizeValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    SetChange(
                        TestMarykModel { set::ref }.change(
                            addValues = setOf(
                                Date(2018, 11, 26),
                                Date(2019, 11, 26),
                                Date(2020, 11, 26),
                                Date(2021, 11, 26)
                            )
                        )
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<TooManyItemsException>(first()).apply {
                    size shouldBe 6u
                }
            }
        }

        // Should not have changed
        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )
        getResponse.values.size shouldBe 1
        getResponse.values.first().values { set } shouldBe setOf(Date(2018, 11, 25), Date(1981, 12, 5))
    }

    @Test
    fun executeChangeSetWithValueValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    SetChange(
                        TestMarykModel { set::ref }.change(
                            addValues = setOf(
                                Date(2101, 12, 33)
                            )
                        )
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<OutOfRangeException>(first()).apply {
                    value shouldBe Date(2101, 12, 33).toString()
                }
            }
        }

        // Should not have changed
        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )
        getResponse.values.size shouldBe 1
        getResponse.values.first().values { set } shouldBe setOf(Date(2018, 11, 25), Date(1981, 12, 5))
    }

    @Test
    fun executeChangeMapWithSizeValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { map.refAt(Time(1, 2, 3)) } with "test1",
                        TestMarykModel { map.refAt(Time(2, 3, 4)) } with "test2",
                        TestMarykModel { map.refAt(Time(3, 4, 5)) } with "test3",
                        TestMarykModel { map.refAt(Time(4, 5, 6)) } with "test4"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<TooManyItemsException>(first()).apply {
                    size shouldBe 6u
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { map } shouldBe mapOf(
            Time(12, 33, 45) to "another",
            Time(13, 44, 55) to "another2"
        )
    }

    @Test
    fun executeChangeMapContentValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { map.refAt(Time(23, 52, 53)) } with "test1",
                        TestMarykModel { map.refAt(Time(1, 52, 53)) } with "verylongwrongsize"
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 2
                shouldBeOfType<OutOfRangeException>(first()).apply {
                    reference shouldBe TestMarykModel { map refToKey Time(23, 52, 53) }
                }
                shouldBeOfType<InvalidSizeException>(this[1]).apply {
                    reference shouldBe TestMarykModel { map refAt Time(1, 52, 53) }
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        getResponse.values.size shouldBe 1
        getResponse.values.first().values { map } shouldBe mapOf(
            Time(12, 33, 45) to "another",
            Time(13, 44, 55) to "another2"
        )
    }

    @Test
    fun executeChangeListSizeValidationExceptionRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Delete(
                        TestMarykModel { listOfString.refAt(0u) }
                    )
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                size shouldBe 1
                shouldBeOfType<NotEnoughItemsException>(first()).apply {
                    size shouldBe 0u
                }
            }
        }
    }
}
