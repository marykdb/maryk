package maryk.datastore.test

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.key
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
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
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.assertIs
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
                    embeddedValues with EmbeddedMarykModel.create { value with "value" }
                    list with listOf(1, 4, 6)
                    listOfString with listOf("c", "d", "e")
                    map with mapOf(LocalTime(12, 33, 45) to "another", LocalTime(13, 44, 55) to "another2")
                    set with setOf(LocalDate(2018, 11, 25), LocalDate(1981, 12, 5))
                    incMap with mapOf(1u to "a", 2u to "b")
                },
                TestMarykModel.create {
                    string with "haha3"
                    int with 6
                    uint with 12u
                    double with 1333.3
                    dateTime with LocalDateTime(2018, 12, 9, 0, 0)
                    bool with false
                    reference with TestMarykModel.key("AAACKwEAAw")
                },
                TestMarykModel.create {
                    string with "haha4"
                    int with 4
                    uint with 14u
                    double with 1.644
                    dateTime with LocalDateTime(2019, 1, 2, 0, 0)
                    bool with false
                    multi with TypedValue(S1, "string")
                    listOfString with listOf("f", "g", "h")
                    map with mapOf(LocalTime(1, 33, 45) to "an other", LocalTime(13, 44, 55) to "an other2")
                    set with setOf(LocalDate(2015, 11, 25), LocalDate(2001, 12, 5))
                },
                TestMarykModel.create {
                    string with "haha5"
                    int with 5
                    uint with 13u
                    double with 3.44
                    dateTime with LocalDateTime(1, 1, 2, 0, 0)
                    bool with true
                    multi with TypedValue(S1, "v1")
                    listOfString with listOf("f", "g", "h")
                    map with mapOf(LocalTime(3, 3, 3) to "three", LocalTime(4, 4, 4) to "4")
                    set with setOf(LocalDate(2001, 1, 1), LocalDate(2002, 2, 2))
                },
                TestMarykModel.create { string with "haha6"; int with 1; uint with 13u; double with 3.44; dateTime with LocalDateTime(1, 1, 2, 0, 0); bool with false }
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

    private suspend fun executeChangeCheckRequest() {
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
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        changeResponse.statuses[1].let { status ->
            val validationFail = assertStatusIs<ValidationFail<*>>(status)
            expect(1) { validationFail.exceptions.size }
            assertIs<InvalidValueException>(validationFail.exceptions[0])
        }

        changeResponse.statuses[2].let { status ->
            val validationFail = assertStatusIs<ValidationFail<*>>(status)
            expect(1) { validationFail.exceptions.size }
            assertIs<InvalidValueException>(validationFail.exceptions[0])
        }
    }

    private suspend fun executeChangeChangeRequest() {
        val newIntList = listOf(1, 2, 3)
        val newDateSet = setOf(LocalDate(2019, 1, 19), LocalDate(2019, 1, 18))
        val newValues = EmbeddedMarykModel.create { value with "Different" }

        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { string::ref } with "haha3",
                        TestMarykModel { listOfString refAt 0u } with "z",
                        TestMarykModel { map refAt LocalTime(12, 33, 45) } with "changed",
                        TestMarykModel { list::ref } with newIntList,
                        TestMarykModel { set::ref } with newDateSet,
                        TestMarykModel { embeddedValues::ref } with newValues
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
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        getResponse.values.first().let {
            expect("haha3") { it.values { string } }
            expect("z") { it.values { listOfString }?.get(0) }
            expect("changed") { it.values { map }?.get(LocalTime(12, 33, 45)) }
            expect(newIntList) { it.values { list } }
            expect(newDateSet) { it.values { set } }
            expect(newValues) { it.values { embeddedValues } }
        }
    }

    private suspend fun executeChangeChangeListItemDoesNotExistRequest() {
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
        assertIs<ServerFail<*>>(changeResponse.statuses[0])
    }

    private suspend fun executeChangeChangeMapDoesNotExistRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[5].change(
                    Change(
                        TestMarykModel { map refAt LocalTime(1, 2, 3) } with "new"
                    )
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        assertIs<ServerFail<*>>(changeResponse.statuses[0])
    }

    private suspend fun executeChangeChangeEmbedDoesNotExistRequest() {
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
        assertIs<ServerFail<*>>(changeResponse.statuses[0])
    }

    private suspend fun executeChangeDeleteRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[2].change(
                    Change(TestMarykModel { reference::ref } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[2])
        )

        expect(1) { getResponse.values.size }
        assertNull(getResponse.values.first().values { reference })
    }

    private suspend fun executeChangeDeleteComplexRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[3].change(
                    Change(TestMarykModel { map::ref } with null),
                    Change(TestMarykModel { listOfString::ref } with null),
                    Change(TestMarykModel { set::ref } with null),
                    Change(TestMarykModel { multi::ref } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
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

    private suspend fun executeChangeDeleteComplexItemsRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[4].change(
                    Change(TestMarykModel { map refAt LocalTime(3, 3, 3) } with null),
                    Change(TestMarykModel { listOfString refAt 1u } with null),
                    Change(TestMarykModel { set refAt LocalDate(2001, 1, 1) } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        changeResponse.statuses[0].let { status ->
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
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

    private suspend fun executeChangeDeleteFailOnOfTypeRefsItemsRequest()  {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[4].change(
                    Change(TestMarykModel { multi refAtType S1 } with null)
                )
            )
        )

        expect(1) { changeResponse.statuses.size }
        assertIs<ServerFail<*>>(changeResponse.statuses[0])
    }

    private suspend fun executeChangeListRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    Change(
                        TestMarykModel { listOfString.refAt(1u) } with null
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
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(listOf("zero", "a", "x", "y", "z")) { getResponse.values.first().values { listOfString } }
    }

    private suspend fun executeChangeSetRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { set.refAt(LocalDate(2018, 11, 25)) } with null
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
            val success = assertStatusIs<ChangeSuccess<*>>(status)
            assertRecent(success.version, 7000uL)
        }

        val getResponse = dataStore.execute(
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        expect(setOf(LocalDate(2018, 11, 26), LocalDate(1981, 12, 5))) { getResponse.values.first().values { set } }
    }

    private suspend fun executeChangeMapRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { map.refAt(LocalTime(1, 2, 3)) } with "test1",
                        TestMarykModel { map.refAt(LocalTime(2, 3, 4)) } with "test2"
                    ),
                    Change(
                        TestMarykModel { map.refAt(LocalTime(12, 33, 45)) } with null
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
            TestMarykModel.get(keys[1])
        )

        expect(1) { getResponse.values.size }
        expect(
            mapOf(
                LocalTime(13, 44, 55) to "another2",
                LocalTime(1, 2, 3) to "test1",
                LocalTime(2, 3, 4) to "test2"
            )
        ) { getResponse.values.first().values { map } }
    }

    private suspend fun executeChangeIncMapRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[1].change(
                    Change(
                        TestMarykModel { incMap.refAt(1u) } with "newA"
                    ),
                    Change(
                        TestMarykModel { incMap.refAt(2u) } with null
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
            assertStatusIs<ChangeSuccess<*>>(status).apply {
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
                assertRecent(version, 7000uL)
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
