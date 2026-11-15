package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.graph.graph
import maryk.core.query.pairs.with
import maryk.datastore.indexeddb.processors.PreparedCurrentSnapshot
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshotRecord
import maryk.datastore.indexeddb.processors.encodeCurrentSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private object PreparedMapSnapshot : RootDataModel<PreparedMapSnapshot>() {
    val labels by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val payload by string(2u)
}

private object PreparedNestedFields : DataModel<PreparedNestedFields>() {
    val title by string(1u)
    val payload by string(2u)
}

private object PreparedNestedSnapshot : RootDataModel<PreparedNestedSnapshot>() {
    val details by embed(1u, dataModel = { PreparedNestedFields })
}

class PreparedStructuralSnapshotTest {
    @Test
    fun mapProjectionOwnsOnlySelectedRowsWithinThePreparationBound() = runTest {
        val select = PreparedMapSnapshot.graph { listOf(labels) }
        for (size in listOf(64, 255, 256)) {
            val labels = (0 until size).associate { "key-$it" to "value-$it" }
            val rows = ArrayList<Pair<ByteArray, ByteArray>>()
            PreparedMapSnapshot.create {
                PreparedMapSnapshot.labels with labels; payload with "x".repeat(96 * 1024)
            }.writeToStorage { type, qualifier, definition, value -> rows += qualifier to encodeStorageValue(type, definition, value) }
            val raw = encodeCurrentSnapshot(IndexedDbRecordMeta(1uL, 2uL, false), rows)
            val prepared = PreparedCurrentSnapshot.prepare(PreparedMapSnapshot, raw, select)
            if (size <= 255) {
                assertNull(prepared.bytes)
                assertEquals(size + 1, assertNotNull(prepared.selected).rows.size)
                raw.fill(0)
            } else {
                assertSame(raw, prepared.bytes)
                assertNull(prepared.selected)
            }
            val decoded = assertNotNull(decodeCurrentSnapshotRecord(PreparedMapSnapshot, ByteArray(16), prepared, select) { _, value -> value })
            assertEquals(labels, decoded.values { PreparedMapSnapshot.labels })
            assertNull(decoded.values { payload })
            assertEquals(2uL, decoded.lastVersion)
        }
    }

    @Test
    fun nestedProjectionPreservesParentsWhenEveryRootIsSelected() = runTest {
        val select = PreparedNestedSnapshot.graph { listOf(graph(details) { listOf(title) }) }
        val rows = ArrayList<Pair<ByteArray, ByteArray>>()
        PreparedNestedSnapshot.create {
            details with PreparedNestedFields.create { title with "title"; payload with "x".repeat(96 * 1024) }
        }.writeToStorage { type, qualifier, definition, value -> rows += qualifier to encodeStorageValue(type, definition, value) }
        val raw = encodeCurrentSnapshot(IndexedDbRecordMeta(1uL, 2uL, false), rows)
        val prepared = PreparedCurrentSnapshot.prepare(PreparedNestedSnapshot, raw, select)
        assertNull(prepared.bytes)
        assertEquals(2, assertNotNull(prepared.selected).rows.size)
        raw.fill(0)
        val decoded = assertNotNull(decodeCurrentSnapshotRecord(PreparedNestedSnapshot, ByteArray(16), prepared, select) { _, value -> value })
        val details = assertNotNull(decoded.values { details })
        assertEquals("title", details { title })
        assertNull(details { payload })
    }
}
