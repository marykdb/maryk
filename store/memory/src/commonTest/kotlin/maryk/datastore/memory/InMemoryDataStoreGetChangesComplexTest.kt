package maryk.datastore.memory

import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.pairs.withType
import maryk.core.query.requests.add
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MultiTypeEnum.T1
import maryk.test.models.MultiTypeEnum.T3
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
                        multi = TypedValue(T3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
                        mapStringString = mapOf("a" to "b", "c" to "d"),
                        mapIntObject = mapOf(1u to EmbeddedMarykModel("v1"), 2u to EmbeddedMarykModel("v2")),
                        mapIntMulti = mapOf(
                            1u to TypedValue(T3,
                                EmbeddedMarykModel("v1",
                                    EmbeddedMarykModel("sub1",
                                        EmbeddedMarykModel("sub2")
                                    )
                                )
                            ),
                            2u to TypedValue(T1, "string"),
                            3u to TypedValue(T3,
                                EmbeddedMarykModel("v2",
                                    EmbeddedMarykModel("2sub1",
                                        EmbeddedMarykModel("2sub2")
                                    )
                                )
                            )
                        )
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
                MultiTypeChange(
                    ComplexModel { multi::ref } withType T3,
                    ComplexModel { mapIntMulti.refAt(1u) } withType T3,
                    ComplexModel { mapIntMulti.refAt(3u) } withType T3
                ),
                Change(
                    ComplexModel { multi.withType(T3) { value::ref } } with "u3",
                    ComplexModel { multi.withType(T3) { model { value::ref } } } with "ue3",
                    ComplexModel { mapStringString refAt "a" } with "b",
                    ComplexModel { mapStringString refAt "c" } with "d",
                    ComplexModel { mapIntObject refAt 1u } with Unit,
                    ComplexModel { mapIntObject.at(1u) { value::ref } } with "v1",
                    ComplexModel { mapIntObject refAt 2u } with Unit,
                    ComplexModel { mapIntObject.at(2u) { value::ref } } with "v2",
                    ComplexModel { mapIntMulti.at(1u) { atType(T3) { value::ref } } } with "v1",
                    ComplexModel { mapIntMulti.at(1u) { atType(T3) { model { value::ref } } } } with "sub1",
                    ComplexModel { mapIntMulti.at(1u) { atType(T3) { model { model { value::ref } } } } } with "sub2",
                    ComplexModel { mapIntMulti refAt 2u } with TypedValue(T1, "string"),
                    ComplexModel { mapIntMulti.at(3u) { atType(T3) { value::ref } } } with "v2",
                    ComplexModel { mapIntMulti.at(3u) { atType(T3) { model { value::ref } } } } with "2sub1",
                    ComplexModel { mapIntMulti.at(3u) { atType(T3) { model { model { value::ref } } } } } with "2sub2"
                )
            ))
        )
    }
}
