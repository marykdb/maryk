package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.assertFailsWith
import kotlin.test.expect

class DataStoreGetChangesTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleGetChangesRequest" to ::executeSimpleGetChangesRequest,
        "executeToVersionGetChangesRequest" to ::executeToVersionGetChangesRequest,
        "executeFromVersionGetChangesRequest" to ::executeFromVersionGetChangesRequest,
        "executeGetChangesRequestWithSelect" to ::executeGetChangesRequestWithSelect,
        "executeGetChangesRequestWithMaxVersions" to ::executeGetChangesRequestWithMaxVersions
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            addRequest
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<SimpleMarykModel>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleGetChangesRequest() {
        val changeResult = dataStore.execute(
            SimpleMarykModel.change(
                keys[1].change(Change(SimpleMarykModel { value::ref } with "haha3"))
            )
        )

        var versionAfterChange = 0uL
        for (status in changeResult.statuses) {
            assertStatusIs<ChangeSuccess<SimpleMarykModel>>(status).apply {
                versionAfterChange = this.version
            }
        }

        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray())
        )

        expect(2) { getResponse.changes.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        expect(
            listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    ObjectCreate,
                    Change(SimpleMarykModel { value::ref } with "haha1")
                ))
            )
        ) {
            getResponse.changes[0].changes
        }

        expect(
            listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(ObjectCreate)),
                VersionedChanges(version = versionAfterChange, changes = listOf(
                    Change(SimpleMarykModel { value::ref } with "haha3")
                ))
            )
        ) {
            getResponse.changes[1].changes
        }
    }

    private suspend fun executeToVersionGetChangesRequest() {
        if (dataStore.keepAllVersions) {
            val getResponse = dataStore.execute(
                SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
            )

            expect(FetchByKey) {getResponse.dataFetchType}
            expect(0) { getResponse.changes.size }
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
                )
            }
        }
    }

    private suspend fun executeFromVersionGetChangesRequest() {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray(), fromVersion = lowestVersion + 1uL)
        )

        expect(0) { getResponse.changes.size }
    }

    private suspend fun executeGetChangesRequestWithSelect() {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(
                *keys.toTypedArray(),
                select = SimpleMarykModel.graph {
                    listOf(value)
                }
            )
        )

        expect(2) { getResponse.changes.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        getResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(SimpleMarykModel { value::ref } with "haha1")
                    ))
                )
            ) {
                it.changes
            }
            expect(keys[0]) { it.key }
        }
    }

    private suspend fun executeGetChangesRequestWithMaxVersions() {
        if (dataStore.keepAllVersions) {
            val collectedVersions = mutableListOf<ULong>()

            val change1 = Change(SimpleMarykModel { value::ref } with "ha change 1")
            dataStore.execute(
                SimpleMarykModel.change(
                    keys[1].change(change1)
                )
            ).also {
                assertStatusIs<ChangeSuccess<SimpleMarykModel>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val change2 = Change(SimpleMarykModel { value::ref } with "ha change 2")
            dataStore.execute(
                SimpleMarykModel.change(
                    keys[1].change(change2)
                )
            ).also {
                assertStatusIs<ChangeSuccess<SimpleMarykModel>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val change3 = Change(SimpleMarykModel { value::ref } with "ha change 3")
            dataStore.execute(
                SimpleMarykModel.change(
                    keys[1].change(change3)
                )
            ).also {
                assertStatusIs<ChangeSuccess<SimpleMarykModel>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val getResponse = dataStore.execute(
                SimpleMarykModel.getChanges(
                    keys[1],
                    maxVersions = 2u
                )
            )

            expect(1) { getResponse.changes.size }

            // Mind that Log is sorted in reverse, so it goes back in time going forward
            getResponse.changes[0].let {
                expect(
                    listOf(
                        VersionedChanges(version = lowestVersion, changes = listOf(ObjectCreate)),
                        VersionedChanges(version = collectedVersions[1], changes = listOf(change2)),
                        VersionedChanges(version = collectedVersions[2], changes = listOf(change3))
                    )
                ) { it.changes }
                expect(keys[1]) { it.key }
            }
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    SimpleMarykModel.getChanges(
                        keys[1],
                        maxVersions = 2u
                    )
                )
            }
        }
    }
}
