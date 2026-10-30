package maryk.core.values

import kotlinx.datetime.LocalTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.SubObjectChangeException
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.properties.references.dsl.item
import maryk.core.properties.types.invoke
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ValuesWorkingCopyTest {
    @Test
    fun stagedParentDeletionMakesFollowingChildDeletionANoopAndRejectsChildSetting() {
        val supplied = EmbeddedMarykModel.create(setDefaults = false) { value with "original" }
        val original = TestMarykModel.create(setDefaults = false) { embeddedValues with supplied }
        val originalTyped = ComplexModel.create(setDefaults = false) { multi with T3(supplied) }
        val embedded = TestMarykModel.ref { embeddedValues }
        val embeddedChild = EmbeddedMarykModel.ref(embedded) { value }
        val typed = ComplexModel.ref { multi }
        val typedValue = ComplexModel.ref { multi.atType(T3) }
        val typedChild = EmbeddedMarykModel.ref(typedValue) { value }

        val deleted = original.change(Change(embedded with null, embeddedChild with null))
        assertNull(deleted[embedded])
        assertFailsWith<SubObjectChangeException> {
            original.change(Change(embedded with null, embeddedChild with "cannot recreate a deleted parent"))
        }
        for (parentDeletion in listOf(typed with null, typedValue with null)) {
            val typedDeleted = originalTyped.change(Change(parentDeletion, typedChild with null))
            assertNull(typedDeleted[typed])
            assertFailsWith<SubObjectChangeException> {
                originalTyped.change(Change(parentDeletion, typedChild with "cannot recreate a deleted parent"))
            }
        }
        assertEquals("original", original[embeddedChild])
        assertEquals(T3(supplied), originalTyped[typed])
        assertEquals("original", supplied { value })
    }

    @Test
    fun explicitEmbeddedReplacementCanClearOrDeleteWithoutMergingOldFields() {
        val populated = EmbeddedMarykModel.create(setDefaults = false) {
            value with "old"
            model with EmbeddedMarykModel.create(setDefaults = false) { value with "retained sibling" }
        }
        val empty = EmbeddedMarykModel.create(setDefaults = false) {}
        val original = TestMarykModel.create(setDefaults = false) { embeddedValues with populated }
        val replaced = original.change(Change(TestMarykModel.ref { embeddedValues } with empty))
        assertEquals(empty, replaced { embeddedValues })
        val deleted = original.change(Change(TestMarykModel.ref { embeddedValues } with empty,
            TestMarykModel.ref { embeddedValues } with null))
        assertNull(deleted { embeddedValues })
        val childDeleted = original.change(Change(TestMarykModel.ref { embeddedValues { value } } with null))
        assertNull(childDeleted[TestMarykModel.ref { embeddedValues { value } }])
        assertEquals("retained sibling", childDeleted[TestMarykModel.ref { embeddedValues { model { value } } }])
        assertEquals(populated, original { embeddedValues })
        assertEquals(0, empty.size)
        assertEquals("old", populated { value })
    }

    @Test
    fun nestedMapAndTypedReplacementsAreCopiedBeforeFollowingChildEdits() {
        val old = EmbeddedMarykModel.create(setDefaults = false) { value with "old" }
        val supplied = EmbeddedMarykModel.create(setDefaults = false) {
            value with "supplied"
            model with EmbeddedMarykModel.create(setDefaults = false) { value with "supplied sibling" }
        }
        val original = ComplexModel.create(setDefaults = false) { mapIntObject with mapOf(1u to old); multi with T3(old) }
        val mapRef = ComplexModel.ref { mapIntObject at 1u }
        val mapChild = EmbeddedMarykModel.ref(mapRef) { value }
        val mapSibling = EmbeddedMarykModel.ref(mapRef) { model { value } }
        val typedRef = ComplexModel.ref { multi.atType(T3) }
        val typedChild = EmbeddedMarykModel.ref(typedRef) { value }
        val changed = original.change(Change(
            mapRef with supplied,
            mapChild with "map edited",
            typedRef with supplied,
            typedChild with "typed edited",
        ))
        assertEquals("map edited", changed[mapChild])
        assertEquals("supplied sibling", changed[mapSibling])
        assertEquals("typed edited", changed[typedChild])
        assertEquals("supplied sibling", (changed { multi }?.value as Values<*>)[EmbeddedMarykModel.ref { model { value } }])
        assertEquals("old", original[mapChild])
        assertEquals("old", original[typedChild])
        assertEquals("supplied", supplied { value })
        assertEquals("supplied sibling", supplied[EmbeddedMarykModel.ref { model { value } }])
    }

    @Test
    fun collectionReplacementThenIncrementalEditsKeepsCallerAndOriginalValuesUnchanged() {
        for (shape in 0..2) {
            val nonempty = shape != 0
            val list = when (shape) { 0 -> emptyList(); 1 -> listOf(5); else -> mutableListOf(5) }
            val map = when (shape) { 0 -> emptyMap(); 1 -> mapOf(7u to "kept"); else -> linkedMapOf(7u to "kept") }
            val set = when (shape) { 0 -> emptySet(); 1 -> setOf("kept"); else -> linkedSetOf("kept") }
            val original = TestMarykModel.create(setDefaults = false) {
                this.list with listOf(99)
                incMap with mapOf(99u to "old")
                setOfString with setOf("old")
            }
            val changed = original.change(
                Change(TestMarykModel.ref { this.list } with list, TestMarykModel.ref { incMap } with map,
                    TestMarykModel.ref { setOfString } with set),
                IncMapAddition(IncMapKeyAdditions(TestMarykModel.ref { incMap }, listOf(8u), listOf("added"))),
                ListChange(TestMarykModel.ref { this.list }.change(addValuesToEnd = listOf(6))),
                SetChange(TestMarykModel.ref { setOfString }.change(addValues = setOf("added"))),
            )
            assertEquals((if (nonempty) listOf(5) else emptyList()) + 6, changed { this.list })
            assertEquals((if (nonempty) mapOf(7u to "kept") else emptyMap()) + (8u to "added"), changed { incMap })
            assertEquals((if (nonempty) setOf("kept") else emptySet()) + "added", changed { setOfString })
            assertEquals(if (nonempty) listOf(5) else emptyList(), list)
            assertEquals(if (nonempty) mapOf(7u to "kept") else emptyMap(), map)
            assertEquals(if (nonempty) setOf("kept") else emptySet(), set)
            assertEquals(listOf(99), original { this.list })
            assertEquals(mapOf(99u to "old"), original { incMap })
            assertEquals(setOf("old"), original { setOfString })
        }
    }

    @Test
    fun repeatedReplacementAndChildEditsUseTheLatestCompleteWorkingValue() {
        val suppliedList = listOf(1, 2)
        val suppliedMap = mapOf(LocalTime(1, 0) to "kept")
        val suppliedEmbed = EmbeddedMarykModel.create(setDefaults = false) {
            value with "caller"
            model with EmbeddedMarykModel.create(setDefaults = false) { value with "nested sibling" }
        }
        val suppliedTyped = S1("caller typed")
        val suppliedSet = setOf("removed", "kept")
        val original = TestMarykModel.create(setDefaults = false) {}
        val changed = original.change(
            Change(TestMarykModel.ref { list } with emptyList<Int>(),
                TestMarykModel.ref { list } with suppliedList,
                TestMarykModel.ref { list.any() } with 8,
                TestMarykModel.ref { list at 0u } with 7,
                TestMarykModel.ref { map } with suppliedMap,
                TestMarykModel.ref { map.anyValue() } with "mapped",
                TestMarykModel.ref { map at LocalTime(2, 0) } with "child",
                TestMarykModel.ref { embeddedValues } with suppliedEmbed,
                TestMarykModel.ref { embeddedValues { value } } with "updated",
                TestMarykModel.ref { multi } with suppliedTyped,
                TestMarykModel.ref { multi.atType(S1) } with "updated typed",
                TestMarykModel.ref { setOfString } with suppliedSet,
                TestMarykModel.ref { setOfString.item("removed") } with null),
            ListChange(TestMarykModel.ref { list }.change(addValuesToEnd = listOf(3))),
        )
        assertEquals(listOf(7, 8, 3), changed { list })
        assertEquals(mapOf(LocalTime(1, 0) to "mapped", LocalTime(2, 0) to "child"), changed { map })
        assertEquals("updated", changed[TestMarykModel.ref { embeddedValues { value } }])
        assertEquals("nested sibling", changed[TestMarykModel.ref { embeddedValues { model { value } } }])
        assertEquals(listOf(1, 2), suppliedList)
        assertEquals(mapOf(LocalTime(1, 0) to "kept"), suppliedMap)
        assertEquals("caller", suppliedEmbed { value })
        assertEquals("nested sibling", suppliedEmbed[EmbeddedMarykModel.ref { model { value } }])
        assertEquals(S1("updated typed"), changed { multi })
        assertEquals(setOf("kept"), changed { setOfString })
        assertEquals(S1("caller typed"), suppliedTyped)
        assertEquals(setOf("removed", "kept"), suppliedSet)
        assertNull(original { list })
        assertNull(original { map })
        assertNull(original { multi })
    }
}
