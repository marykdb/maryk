package maryk.datastore.test

import maryk.core.properties.enum.invoke
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.Or
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The writer permits old case rows to remain: queries must consult the active parent case. */
suspend fun assertRetainedTypedBranchFiltering(
    store: IsDataStore,
    retainChildren: suspend (Key<ComplexModel>, ByteArray, suspend () -> Unit) -> Unit,
) {
    val embedded = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(ComplexModel.create {
        multi with T3 { value with "old" }
    })).statuses.single())
    val list = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(ComplexModel.create {
        multi with T4(listOf("old"))
    })).statuses.single())
    for (added in listOf(embedded, list)) {
        retainChildren(added.key, ComplexModel.ref { multi }.toStorageByteArray()) {
            assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(
                Change(ComplexModel.ref { multi } with T1("new")),
            ))).statuses.single())
        }
        val current = store.execute(ComplexModel.get(added.key)).values.single().values
        assertNull(current[ComplexModel.ref { multi atType T3 }])
        assertNull(current[ComplexModel.ref { multi atType T4 }])
    }
    val activeEmbedded = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(ComplexModel.create {
        multi with T3 { value with "old" }
    })).statuses.single()).key
    val activeList = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(ComplexModel.create {
        multi with T4(listOf("old"))
    })).statuses.single()).key
    val cases = listOf<Pair<IsFilter, Set<Key<ComplexModel>>>>(
        Exists(ComplexModel.ref { multi atType T3 }) to setOf(activeEmbedded),
        Exists(ComplexModel.ref { multi.atType(T3) { value } }) to setOf(activeEmbedded),
        Equals(ComplexModel.ref { multi.atType(T3) { value } } with "old") to setOf(activeEmbedded),
        Equals(ComplexModel.ref { multi atType T3 } with T3 { value with "old" }.value) to setOf(activeEmbedded),
        Exists(ComplexModel.ref { multi.atType(T4) { ::anyItemRef } }) to setOf(activeList),
        Equals(ComplexModel.ref { multi.atType(T4) { ::anyItemRef } } with "old") to setOf(activeList),
    )
    for ((filter, expected) in cases) {
        assertEquals(expected, store.execute(ComplexModel.scan(where = Or(filter), allowTableScan = true))
            .values.map { it.key }.toSet(), "retained current filter=$filter")
        val originalKey = if (expected.single() == activeEmbedded) embedded.key else list.key
        assertEquals(listOf(originalKey), store.execute(ComplexModel.get(originalKey, where = filter, toVersion = list.version))
            .values.map { it.key }, "active historic filter=$filter")
    }
}
