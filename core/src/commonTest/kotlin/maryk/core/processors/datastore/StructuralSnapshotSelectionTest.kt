package maryk.core.processors.datastore

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.references.dsl.at
import maryk.core.query.changes.Change
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertEquals

private object ReplaySelectionModel : RootDataModel<ReplaySelectionModel>() {
    val note by string(1u)
    val entries by list(2u, valueDefinition = StringDefinition())
}

private object DirectMapReplayModel : RootDataModel<DirectMapReplayModel>() {
    val note by string(1u)
    val entries by map(2u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class StructuralSnapshotSelectionTest {
    @Test
    fun cappedScalarMapCellsRecoverTheirCompleteCreationSnapshot() {
        val cells = listOf(
            Triple(DirectMapReplayModel.entries.ref().toStorageByteArray(), 1uL, 2),
            Triple(DirectMapReplayModel.ref { entries.at("first") }.toStorageByteArray(), 2uL, "new"),
            Triple(DirectMapReplayModel.ref { entries.at("second") }.toStorageByteArray(), 1uL, "b"),
        ).sortedWith { a, b -> a.first.compareTo(b.first) }
        var position = -1
        var snapshots = 0
        val changes = DirectMapReplayModel.readStorageToChanges(
            getQualifier = { handler ->
                cells.getOrNull(++position)?.first?.let { qualifier ->
                    handler({ qualifier[it] }, qualifier.size)
                    true
                } ?: false
            },
            select = null,
            creationVersion = 1uL,
            readSelectedValuesAtVersion = { version, _ ->
                snapshots++
                assertEquals(1uL, version)
                DirectMapReplayModel.create { entries with mapOf("first" to "old", "second" to "b") }
            },
            processValue = { _, _, reader -> reader(cells[position].second, cells[position].third) },
        )
        val before = DirectMapReplayModel.create { note with "retained" }
        val initial = before.change(changes.first().changes)
        assertEquals(mapOf("first" to "old", "second" to "b"), initial { entries })
        val updated = initial.change(changes.last().changes)
        assertEquals(mapOf("first" to "new", "second" to "b"), updated { entries })
        assertEquals(1, snapshots)
    }

    @Test
    fun scalarMapCreationUsesDeltasAndLaterStructuresKeepSnapshotFallback() {
        val root = DirectMapReplayModel.entries.ref().toStorageByteArray()
        val first = DirectMapReplayModel.ref { entries.at("first") }.toStorageByteArray()
        val second = DirectMapReplayModel.ref { entries.at("second") }.toStorageByteArray()
        fun replay(original: Map<String, String>?, cells: List<Pair<ByteArray, Any?>>, expected: Map<String, String>?, creationVersion: ULong? = null, needsSnapshot: Boolean = false) {
            var position = -1
            var snapshotReads = 0
            val sorted = cells.sortedWith { a, b -> a.first.compareTo(b.first) }
            val changes = DirectMapReplayModel.readStorageToChanges(
                getQualifier = { handler ->
                    sorted.getOrNull(++position)?.first?.let { qualifier ->
                        handler({ qualifier[it] }, qualifier.size)
                        true
                    } ?: false
                },
                select = null,
                creationVersion = creationVersion,
                readSelectedValuesAtVersion = { _, _ ->
                    check(needsSnapshot) { "Initial scalar map deltas need no snapshot" }
                    snapshotReads++
                    DirectMapReplayModel.create { entries with expected }
                },
                processValue = { _, _, reader -> reader(1uL, sorted[position].second) },
            )
            val before = DirectMapReplayModel.create { note with "retained"; entries with original }
            val after = before.change(changes.flatMap { it.changes })
            assertEquals(expected, after { entries })
            assertEquals("retained", after { note })
            assertEquals(if (needsSnapshot) 1 else 0, snapshotReads)
        }
        replay(null, listOf(root to 2, first to "a", second to "b"), mapOf("first" to "a", "second" to "b"), creationVersion = 1uL)
        replay(mapOf("first" to "a", "second" to "b"), listOf(root to 1, first to null), mapOf("second" to "b"), needsSnapshot = true)
        replay(null, listOf(root to 1, first to "a"), mapOf("first" to "a"), needsSnapshot = true)
        replay(mapOf("first" to "a"), listOf(root to 0, first to null), emptyMap())
        replay(mapOf("first" to "a"), listOf(root to null, first to null), null)
    }

    @Test
    fun retainsCollectionDeletionWhenChildTombstonesShareItsVersion() {
        val qualifiers = listOf(
            ReplaySelectionModel.entries.ref().toStorageByteArray(),
            ReplaySelectionModel.ref { entries.at(0u) }.toStorageByteArray(),
        )
        var position = -1
        val changes = ReplaySelectionModel.readStorageToChanges(
            getQualifier = { handler ->
                qualifiers.getOrNull(++position)?.let { qualifier ->
                    handler({ qualifier[it] }, qualifier.size)
                    true
                } ?: false
            },
            select = null,
            creationVersion = null,
            readSelectedValuesAtVersion = { _, _ -> ReplaySelectionModel.create { note with "retained" } },
            processValue = { _, _, reader -> reader(2uL, null) },
        )
        val pairs = changes.single().changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }
        assertEquals(mapOf("entries" to null), pairs.associate { it.reference.completeName to it.value })
    }

    @Test
    fun reconstructsOnlyStructuralRootsAndRetainsUnrelatedChanges() {
        val cells = listOf(
            ReplaySelectionModel.note.ref().toStorageByteArray() to "large unrelated field",
            ReplaySelectionModel.entries.ref().toStorageByteArray() to 2,
            ReplaySelectionModel.ref { entries.at(0u) }.toStorageByteArray() to "first",
            ReplaySelectionModel.ref { entries.at(1u) }.toStorageByteArray() to "second",
        ).sortedWith { first, second -> first.first.compareTo(second.first) }
        var position = -1
        var snapshotReads = 0
        val changes = ReplaySelectionModel.readStorageToChanges(
            getQualifier = { handler ->
                cells.getOrNull(++position)?.first?.let { qualifier ->
                    handler({ qualifier[it] }, qualifier.size)
                    true
                } ?: false
            },
            select = null,
            creationVersion = 1uL,
            readSelectedValuesAtVersion = { version, selection ->
                snapshotReads++
                assertEquals(1uL, version)
                assertEquals(listOf(2u), selection.properties.map { it.index })
                ReplaySelectionModel.create { entries with listOf("first", "second") }
            },
            processValue = { _, _, reader -> reader(1uL, cells[position].second) },
        )
        assertEquals(1, snapshotReads)
        val pairs = changes.single().changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }
        assertEquals(
            mapOf("note" to "large unrelated field", "entries" to listOf("first", "second")),
            pairs.associate { it.reference.completeName to it.value },
        )
    }
}
