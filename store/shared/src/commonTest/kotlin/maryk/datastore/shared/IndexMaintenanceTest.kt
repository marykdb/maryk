package maryk.datastore.shared

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private object IndexDetails : DataModel<IndexDetails>() {
    val name by string(1u)
}

private object IndexRecord : RootDataModel<IndexRecord>() {
    val name by string(1u)
    val status by string(2u)
    val active by boolean(3u)
    val details by embed(4u, dataModel = { IndexDetails })
    val notes by list(5u, valueDefinition = StringDefinition())
    val tags by set(6u, valueDefinition = StringDefinition())
    val labels by map(7u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class IndexMaintenanceTest {
    private val index = Multiple(IndexRecord.name.ref(), IndexRecord.active.ref())
    private val indexes = listOf<IsIndexable>(index)

    @Test
    fun unrelatedScalarChangesLeaveCompositeIndexesUntouched() {
        val reference = IndexRecord.status.ref()
        assertEquals(emptyList(), indexes.indexesToReconcile(listOf(Change(reference with "reviewed")), listOf(reference), false))
    }

    @Test
    fun changingEitherCompositeFieldRequiresMaintenance() {
        val reference = IndexRecord.active.ref()
        assertEquals(indexes, indexes.indexesToReconcile(listOf(Change(reference with false)), listOf(reference), false))
    }

    @Test
    fun unrelatedRootCollectionReplacementsSkipScalarIndexes() {
        for ((reference, change) in listOf(
            IndexRecord.notes.ref() to Change(IndexRecord.notes.ref() with listOf("note")),
            IndexRecord.tags.ref() to Change(IndexRecord.tags.ref() with setOf("tag")),
            IndexRecord.labels.ref() to Change(IndexRecord.labels.ref() with mapOf("label" to "value")),
        )) {
            assertEquals(emptyList(), indexes.indexesToReconcile(listOf(change), listOf(reference), false))
        }
    }

    @Test
    fun structuralListAndSetChangesDiscoverTheirOwnReferences() {
        val changes = listOf(
            ListChange(IndexRecord.notes.ref().change(addValuesToEnd = listOf("note"))),
            SetChange(IndexRecord.tags.ref().change(addValues = setOf("tag"))),
        )
        assertEquals(emptyList(), indexes.indexesToReconcile(changes, emptyList(), false))
        val active = IndexRecord.active.ref()
        assertEquals(indexes, indexes.indexesToReconcile(
            changes + Change(active with true), listOf(active), false,
        ))
    }

    @Test
    fun deletionChecksAndStructuralChangesRetainAllIndexes() {
        val reference = IndexRecord.status.ref()
        val change = listOf(Change(reference with "reviewed"))
        assertSame(indexes, indexes.indexesToReconcile(change, listOf(reference), true))
        assertSame(indexes, indexes.indexesToReconcile(listOf(Check(reference with "reviewed")), listOf(reference), false))
        val parent = IndexRecord.details.ref()
        assertSame(indexes, indexes.indexesToReconcile(
            listOf(Change(parent with IndexDetails.create { name with "replacement" })), listOf(parent), false,
        ))
    }

    @Test
    fun customReferenceAliasesRetainIndexMaintenance() {
        val reference = alias(IndexRecord.name.ref())
        assertSame(indexes, indexes.indexesToReconcile(listOf(Change(reference with "changed")), listOf(reference), false))
    }

    private fun <T : Any, D : IsPropertyDefinition<T>, V : Any> alias(reference: IsPropertyReference<T, D, V>): IsPropertyReference<T, D, V> =
        object : IsPropertyReference<T, D, V> by reference {}

    @Test
    fun customIndexDependenciesAreNeverInferred() {
        val custom = object : IsIndexable by index {}
        val reference = IndexRecord.status.ref()
        assertEquals(listOf(custom), listOf(custom).indexesToReconcile(listOf(Change(reference with "reviewed")), listOf(reference), false))
    }
}
