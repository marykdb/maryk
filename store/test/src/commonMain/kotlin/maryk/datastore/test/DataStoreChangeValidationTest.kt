package maryk.datastore.test

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.key
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore
import maryk.test.models.TestMarykModel
import kotlin.test.assertIs
import kotlin.test.expect

class DataStoreChangeValidationTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<TestMarykModel>>()
    private val lastVersions = mutableListOf<ULong>()

    override val allTests = mapOf(
        "executeChangeChangeWithValidationExceptionRequest" to ::executeChangeChangeWithValidationExceptionRequest,
        "executeChangeDeleteWithValidationExceptionRequest" to ::executeChangeDeleteWithValidationExceptionRequest,
        "executeChangeListWithTooManyItemsValidationExceptionRequest" to ::executeChangeListWithTooManyItemsValidationExceptionRequest,
        "executeChangeListWithContentValidationExceptionRequest" to ::executeChangeListWithContentValidationExceptionRequest,
        "executeChangeSetWithMaxSizeValidationExceptionRequest" to ::executeChangeSetWithMaxSizeValidationExceptionRequest,
        "executeChangeSetWithValueValidationExceptionRequest" to ::executeChangeSetWithValueValidationExceptionRequest,
        "executeChangeMapWithSizeValidationExceptionRequest" to ::executeChangeMapWithSizeValidationExceptionRequest,
        "executeChangeMapContentValidationExceptionRequest" to ::executeChangeMapContentValidationExceptionRequest,
        "executeChangeListSizeValidationExceptionRequest" to ::executeChangeListSizeValidationExceptionRequest
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            TestMarykModel.add(
                TestMarykModel.create {
                    string with "haha1"
                    int with 5
                    uint with 6u
                    double with 0.43
                    dateTime with LocalDateTime(2018, 3, 2, 0, 0)
                    bool with true
                    listOfString with listOf("a", "b", "c")
                    map with mapOf(LocalTime(2, 3, 5) to "test")
                    set with setOf(LocalDate(2018, 3, 4))
                },
                TestMarykModel.create {
                    string with "haha2"
                    int with 3
                    uint with 8u
                    double with 1.244
                    dateTime with LocalDateTime(2018, 1, 2, 0, 0)
                    bool with false
                    listOfString with listOf("c", "d", "e")
                    map with mapOf(LocalTime(12, 33, 45) to "another", LocalTime(13, 44, 55) to "another2")
                    set with setOf(LocalDate(2018, 11, 25), LocalDate(1981, 12, 5))
                },
                TestMarykModel.create {
                    string with "haha3"
                    int with 6
                    uint with 12u
                    double with 1333.3
                    dateTime with LocalDateTime(2018, 12, 9, 0, 0)
                    bool with false
                    listOfString with listOf("c")
                    reference with TestMarykModel.key("AAACKwEAAw")
                }
            )
        )

        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<TestMarykModel>>(status)
            keys.add(response.key)
            lastVersions.add(response.version)
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lastVersions.clear()
    }

    private suspend fun executeChangeChangeWithValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { string::ref } with "wrong"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertStatusIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<InvalidValueException>(first())
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        // Should not have changes
        expect(1) { getResponse.values.size }
        expect("haha2") { getResponse.values.first().values { string } }
    }

    private suspend fun executeChangeDeleteWithValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Change(TestMarykModel { uint::ref } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertStatusIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<AlreadySetException>(first())
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[2])
        )

        // Should stay the same
        expect(1) { getResponse.values.size }
        expect(12u) { getResponse.values.first().values { uint } }
    }

    private suspend fun executeChangeListWithTooManyItemsValidationExceptionRequest() {
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

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<TooManyItemsException>(first()).apply {
                    expect(9u) { size }
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(listOf("a", "b", "c")) { getResponse.values.first().values { listOfString } }
    }

    private suspend fun executeChangeListWithContentValidationExceptionRequest() {
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

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<InvalidSizeException>(first()).apply {
                    expect(TestMarykModel { listOfString refAt 0u }) { reference }
                    expect("verylongwrongvalue") { value }
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(listOf("a", "b", "c")) { getResponse.values.first().values { listOfString } }
    }

    private suspend fun executeChangeSetWithMaxSizeValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    SetChange(
                        TestMarykModel { set::ref }.change(
                            addValues = setOf(
                                LocalDate(2018, 11, 26),
                                LocalDate(2019, 11, 26),
                                LocalDate(2020, 11, 26),
                                LocalDate(2021, 11, 26)
                            )
                        )
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<TooManyItemsException>(first()).apply {
                    expect(6u) { size }
                }
            }
        }

        // Should not have changed
        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )
        expect(1) { getResponse.values.size }
        expect(setOf(LocalDate(2018, 11, 25), LocalDate(1981, 12, 5))) { getResponse.values.first().values { set } }
    }

    private suspend fun executeChangeSetWithValueValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    SetChange(
                        TestMarykModel { set::ref }.change(
                            addValues = setOf(
                                LocalDate(2101, 12, 31)
                            )
                        )
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertStatusIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<OutOfRangeException>(first()).apply {
                    expect(LocalDate(2101, 12, 31).toString()) { value }
                }
            }
        }

        // Should not have changed
        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )
        expect(1) { getResponse.values.size }
        expect(setOf(LocalDate(2018, 11, 25), LocalDate(1981, 12, 5))) { getResponse.values.first().values { set } }
    }

    private suspend fun executeChangeMapWithSizeValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { map.refAt(LocalTime(1, 2, 3)) } with "test1",
                        TestMarykModel { map.refAt(LocalTime(2, 3, 4)) } with "test2",
                        TestMarykModel { map.refAt(LocalTime(3, 4, 5)) } with "test3",
                        TestMarykModel { map.refAt(LocalTime(4, 5, 6)) } with "test4"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<TooManyItemsException>(first()).apply {
                    expect(6u) { size }
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }

        expect(
            mapOf(
                LocalTime(12, 33, 45) to "another",
                LocalTime(13, 44, 55) to "another2"
            )
        ) {
            getResponse.values.first().values { map }
        }
    }

    private suspend fun executeChangeMapContentValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { map.refAt(LocalTime(23, 52, 53)) } with "test1",
                        TestMarykModel { map.refAt(LocalTime(1, 52, 53)) } with "verylongwrongsize"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(2) { size }
                assertIs<OutOfRangeException>(first()).apply {
                    expect(TestMarykModel { map refToKey LocalTime(23, 52, 53) }) { reference }
                }
                assertIs<InvalidSizeException>(this[1]).apply {
                    expect(TestMarykModel { map refAt LocalTime(1, 52, 53) }) { reference }
                }
            }
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        expect(
            mapOf(
                LocalTime(12, 33, 45) to "another",
                LocalTime(13, 44, 55) to "another2"
            )
        ) {
            getResponse.values.first().values { map }
        }
    }

    private suspend fun executeChangeListSizeValidationExceptionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Change(
                        TestMarykModel { listOfString.refAt(0u) } with null
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val validationFail = assertStatusIs<ValidationFail<*>>(status)
            validationFail.exceptions.apply {
                expect(1) { size }
                assertIs<NotEnoughItemsException>(first()).apply {
                    expect(0u) { size }
                }
            }
        }
    }
}
