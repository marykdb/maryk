package maryk.core.processors.datastore

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.exceptions.StorageException
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.core.properties.types.invoke
import maryk.core.properties.references.ListReference
import maryk.core.values.Values
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object DeletedListDetail : DataModel<DeletedListDetail>() {
    val value by string(1u, required = false)
}

private object DeletedListRecord : RootDataModel<DeletedListRecord>() {
    val embedded by list(1u, valueDefinition = EmbeddedValuesDefinition(dataModel = { DeletedListDetail }))
    val typed by list(2u, valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
    val typedMap by map(3u, keyDefinition = StringDefinition(), valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
    val direct by multiType(4u, typeEnum = MarykTypeEnum, required = false)
    val following by string(5u)
}

class DeletedComplexListStorageTest {
    private data class Cell(val qualifier: ByteArray, val value: Any?)

    private fun cells(values: Values<DeletedListRecord>): List<Cell> = buildList {
        values.writeToStorage { _, qualifier, _, value -> add(Cell(qualifier, value)) }
    }

    private fun decode(cells: List<Cell>): Values<DeletedListRecord> {
        val sorted = cells.sortedWith { first, second -> first.qualifier.compareTo(second.qualifier) }
        var current = -1
        return DeletedListRecord.readStorageToValues(
            getQualifier = { reader ->
                sorted.getOrNull(++current)?.qualifier?.let { reader({ index -> it[index] }, it.size); true } ?: false
            },
            select = null,
            processValue = { _, _ -> sorted[current].value },
        )
    }

    private fun <T : Any> readList(reference: ListReference<T, *>, cells: List<Cell>): MutableList<T>? {
        val prefix = reference.toStorageByteArray()
        val selected = cells.filter { it.qualifier.matchesRangePart(0, prefix) }
            .sortedWith { first, second -> first.qualifier.compareTo(second.qualifier) }
        var current = -1
        return readStorageToList(reference,
            getQualifier = { reader -> selected.getOrNull(++current)?.qualifier?.let { reader({ i -> it[i] }, it.size); true } ?: false },
            processValue = { _, _ -> selected[current].value })
    }

    @Test
    fun standaloneListReaderPreservesCompleteEmbeddedAndTypedItems() {
        val values = DeletedListRecord.create(setDefaults = false) {
            embedded with listOf(DeletedListDetail.create { value with "first" }, DeletedListDetail.create { value with "second" })
            typed with listOf(MarykTypeEnum.T1("simple"), MarykTypeEnum.T3(EmbeddedMarykModel.create { value with "embedded" }), MarykTypeEnum.T4(listOf("nested")))
            following with "following"
        }
        val stored = cells(values)
        assertEquals(values { embedded }, readList(DeletedListRecord.ref { embedded }, stored))
        assertEquals(values { typed }, readList(DeletedListRecord.ref { typed }, stored))
    }

    @Test
    fun standaloneListReaderRejectsMissingCountAndMissingLastItem() {
        val values = DeletedListRecord.create(setDefaults = false) {
            embedded with listOf(DeletedListDetail.create { value with "first" }, DeletedListDetail.create { value with "second" })
            following with "following"
        }
        val reference = DeletedListRecord.ref { embedded }
        val prefix = reference.toStorageByteArray()
        val last = DeletedListRecord.ref { embedded at 1u }.toStorageByteArray()
        assertFailsWith<StorageException> { readList(reference, cells(values).filterNot { it.qualifier.contentEquals(prefix) }) }
        assertFailsWith<StorageException> { readList(reference, cells(values).filterNot { it.qualifier.matchesRangePart(0, last) }) }
    }

    @Test
    fun clearedEmbeddedListIgnoresRetainedItemAndChildTombstones() {
        val initial = DeletedListRecord.create(setDefaults = false) {
            embedded with listOf(
                DeletedListDetail.create { value with "first" },
                DeletedListDetail.create { value with "second" },
            )
            following with "outside the deleted prefix"
        }
        val list = DeletedListRecord.ref { embedded }.toStorageByteArray()
        val deleted = cells(initial).map { cell ->
            when {
                cell.qualifier.contentEquals(list) -> cell.copy(value = 0)
                cell.qualifier.matchesRangePart(0, list) -> cell.copy(value = null)
                else -> cell
            }
        }
        assertEquals(DeletedListRecord.create(setDefaults = false) {
            embedded with emptyList()
            following with "outside the deleted prefix"
        }, decode(deleted))
    }

    @Test
    fun shrinkingEmbeddedListIgnoresDeletedTailEvenWhenOldChildrenRemain() {
        val kept = DeletedListDetail.create { value with "kept" }
        val initial = DeletedListRecord.create(setDefaults = false) {
            embedded with listOf(kept, DeletedListDetail.create { value with "old second" }, DeletedListDetail.create { value with "old third" })
            following with "following"
        }
        val list = DeletedListRecord.ref { embedded }.toStorageByteArray()
        val deletedParents = listOf(1u, 2u).map { index -> DeletedListRecord.ref { embedded at index }.toStorageByteArray() }
        val deleted = cells(initial).map { cell ->
            when {
                cell.qualifier.contentEquals(list) -> cell.copy(value = 1)
                deletedParents.any { it.contentEquals(cell.qualifier) } -> cell.copy(value = null)
                else -> cell
            }
        }
        assertEquals(DeletedListRecord.create(setDefaults = false) {
            embedded with listOf(kept)
            following with "following"
        }, decode(deleted))
    }

    @Test
    fun typedParentTombstonesSuppressOldCasesInListsMapsAndDirectValues() {
        val typed = MarykTypeEnum.T3(EmbeddedMarykModel.create { value with "old typed child" })
        val initial = DeletedListRecord.create(setDefaults = false) {
            this.typed with listOf(typed)
            typedMap with mapOf("key" to typed)
            direct with typed
            following with "following"
        }
        val list = DeletedListRecord.ref { this.typed }.toStorageByteArray()
        val map = DeletedListRecord.ref { typedMap }.toStorageByteArray()
        val deletedParents = listOf(
            DeletedListRecord.ref { this.typed at 0u }.toStorageByteArray(),
            DeletedListRecord.ref { typedMap at "key" }.toStorageByteArray(),
            DeletedListRecord.ref { direct }.toStorageByteArray(),
        )
        val deleted = cells(initial).map { cell ->
            when {
                cell.qualifier.contentEquals(list) || cell.qualifier.contentEquals(map) -> cell.copy(value = 0)
                deletedParents.any { it.contentEquals(cell.qualifier) } -> cell.copy(value = null)
                else -> cell
            }
        }
        assertEquals(DeletedListRecord.create(setDefaults = false) {
            this.typed with emptyList()
            typedMap with emptyMap()
            following with "following"
        }, decode(deleted))
    }
}
