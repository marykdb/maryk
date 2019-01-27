package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.EmbeddedMarykModel.Properties
import maryk.test.models.Option.V3
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class InMemoryDataStoreGetChangesComplexTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<ComplexModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                ComplexModel.add(
                    ComplexModel(
                        multi = TypedValue(V3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3")))
                    )
                )
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<ComplexModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    @Test
    fun executeSimpleGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            ComplexModel.getChanges(*keys.toTypedArray())
        )

        getResponse.changes.size shouldBe 1

        getResponse.changes[0].changes shouldBe listOf(
            VersionedChanges(version = lowestVersion, changes = listOf(
                MultiTypeChange(ComplexModel.ref { multi } with V3),
                Change(
                    ComplexModel { multi.refWithType(V3, Properties) { value } } with "u3",
                    ComplexModel { multi.withType(V3, Properties) { model ref { value } } } with "ue3"
                )
            ))
        )
    }
}
