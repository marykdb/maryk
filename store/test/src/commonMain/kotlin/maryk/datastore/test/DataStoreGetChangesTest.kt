package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
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
        "executeGetChangesRequestWithSelect" to ::executeGetChangesRequestWithSelect
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private fun executeSimpleGetChangesRequest() = runSuspendingTest {
        val changeResult = dataStore.execute(
            SimpleMarykModel.change(
                keys[1].change(Change(SimpleMarykModel { value::ref } with "haha3"))
            )
        )

        var versionAfterChange = 0uL
        for (status in changeResult.statuses) {
            assertType<ChangeSuccess<SimpleMarykModel>>(status).apply {
                versionAfterChange = this.version
            }
        }

        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray())
        )

        expect(2) { getResponse.changes.size }

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

    private fun executeToVersionGetChangesRequest() = runSuspendingTest {
        if (dataStore.keepAllVersions) {
            val getResponse = dataStore.execute(
                SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
            )

            expect(0) { getResponse.changes.size }
        } else {
            assertFailsWith<RequestException> {
                runSuspendingTest {
                    dataStore.execute(
                        SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
                    )
                }
            }
        }
    }

    private fun executeFromVersionGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray(), fromVersion = lowestVersion + 1uL)
        )

        expect(0) { getResponse.changes.size }
    }

    private fun executeGetChangesRequestWithSelect() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            SimpleMarykModel.getChanges(
                *keys.toTypedArray(),
                select = SimpleMarykModel.graph {
                    listOf(value)
                }
            )
        )

        expect(2) { scanResponse.changes.size }

        scanResponse.changes[0].let {
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
}
