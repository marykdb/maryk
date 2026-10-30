package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.reference
import maryk.core.query.filters.Equals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.Or
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertIs

object StructuralFilterSource : RootDataModel<StructuralFilterSource>() {
    val target by reference(1u, dataModel = { ComplexModel })
}

/** Exact, wildcard and object-reference paths must compare reconstructed structures. */
suspend fun assertStructuralFilterEquivalence(store: IsDataStore) {
    val value = EmbeddedMarykModel.create { value with "embedded" }
    val target = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(ComplexModel.create {
        mapIntObject with mapOf(1u to value)
        mapWithList with mapOf("list" to listOf("first", "second"))
        mapWithSet with mapOf("set" to setOf("first", "second"))
        mapWithMap with mapOf("map" to mapOf("key" to "value"))
    })).statuses.single()).key
    val source = assertIs<AddSuccess<StructuralFilterSource>>(store.execute(StructuralFilterSource.add(StructuralFilterSource.create {
        this.target with target
    })).statuses.single())
    val direct = listOf<IsFilter>(
        Equals(ComplexModel.ref { mapIntObject at 1u } with value),
        Equals(ComplexModel.ref { mapIntObject.anyValue() } with value),
        Equals(ComplexModel.ref { mapWithList at "list" } with listOf("first")),
        Equals(ComplexModel.ref { mapWithSet at "set" } with setOf("first")),
        Equals(ComplexModel.ref { mapWithMap at "map" } with mapOf("key" to "value")),
    )
    for (version in listOf(null, source.version)) for (filter in direct) {
        assertEquals(listOf(target), store.execute(ComplexModel.scan(where = Or(filter), allowTableScan = true, toVersion = version))
            .values.map { it.key }, "direct structure filter=$filter version=$version")
    }
    val referenced = listOf<IsFilter>(
        Equals(StructuralFilterSource.ref { this.target { mapIntObject at 1u } } with value),
        Equals(StructuralFilterSource.ref { this.target { mapIntObject.anyValue() } } with value),
        Equals(StructuralFilterSource.ref { this.target { mapWithList at "list" } } with listOf("first")),
        Equals(StructuralFilterSource.ref { this.target { mapWithSet at "set" } } with setOf("first")),
        Equals(StructuralFilterSource.ref { this.target { mapWithMap at "map" } } with mapOf("key" to "value")),
    )
    for (version in listOf(null, source.version)) for (filter in referenced) {
        assertEquals(listOf(source.key), store.execute(StructuralFilterSource.scan(where = Or(filter), allowTableScan = true, toVersion = version))
            .values.map { it.key }, "referenced structure filter=$filter version=$version")
    }
}
