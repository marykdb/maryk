package maryk.core.values

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.SubObjectChangeException
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private object TraversalItem : DataModel<TraversalItem>() {
    val label by string(1u, required = false)
    val numbers by list(2u, required = false, valueDefinition = NumberDefinition(type = SInt32))
    val tags by set(3u, required = false, valueDefinition = StringDefinition())
    val allocated by incrementingMap(4u, required = false, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val children by map(5u, required = false, keyDefinition = NumberDefinition(type = UInt32),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
}

private object TraversalRecord : RootDataModel<TraversalRecord>() {
    val entries by map(1u, required = false, keyDefinition = NumberDefinition(type = UInt32),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { TraversalItem }))
    val boxes by list(2u, required = false, valueDefinition = EmbeddedValuesDefinition(dataModel = { TraversalItem }))
}

class ValuesTraversalMetamorphicTest {
    private fun item(label: String) = TraversalItem.create(setDefaults = false) {
        this.label with label
        numbers with listOf(1, 2)
        tags with setOf("kept")
        allocated with mapOf(1u to "old")
        children with linkedMapOf(
            1u to EmbeddedMarykModel.create(setDefaults = false) { value with "$label-a" },
            2u to EmbeddedMarykModel.create(setDefaults = false) { value with "$label-b" },
        )
    }

    @Test
    fun mapWildcardChildChangesEqualConcreteChangesAndCanBeReversed() {
        val original = TraversalRecord.create(setDefaults = false) {
            entries with linkedMapOf(1u to item("one"), 2u to item("two"), 3u to item("three"))
        }
        val wildcard = TraversalItem.ref(TraversalRecord.ref { entries.anyValue() }) { label }
        val changed = original.change(Change(wildcard with "changed"))
        val concrete = original.change(Change(*(1u..3u).map { key ->
            TraversalItem.ref(TraversalRecord.ref { entries at key }) { label } with "changed"
        }.toTypedArray()))
        assertEquals(concrete, changed)
        assertEquals(listOf("changed", "changed", "changed"), changed { entries }!!.values.map { it { label } })
        val reversed = changed.change(Change(*(1u..3u).map { key ->
            TraversalItem.ref(TraversalRecord.ref { entries at key }) { label } with original { entries }!!.getValue(key)[TraversalItem.ref { label }]
        }.toTypedArray()))
        assertEquals(original, reversed)

        val removed = original.change(Change(wildcard with null))
        assertEquals(3, removed { entries }!!.size)
        for (value in removed { entries }!!.values) {
            assertNull(value { label })
            assertEquals(listOf(1, 2), value { numbers })
        }
        assertEquals(listOf("one", "two", "three"), original { entries }!!.values.map { it { label } })
    }

    @Test
    fun listAndNestedWildcardsRestartTraversalForEveryBranch() {
        val original = TraversalRecord.create(setDefaults = false) { boxes with listOf(item("one"), item("two")) }
        val anyBox = TraversalRecord.ref { boxes.any() }
        val labelReference = TraversalItem.ref(anyBox) { label }
        val anyChild = TraversalItem.ref(anyBox) { children.anyValue() }
        val childLabel = EmbeddedMarykModel.ref(anyChild) { value }
        val changed = original.change(Change(labelReference with "box", childLabel with "child"))
        assertEquals(listOf("box", "box"), changed { boxes }!!.map { it { label } })
        assertEquals(listOf("child", "child", "child", "child"), changed { boxes }!!.flatMap {
            it { children }!!.values.map { child -> child { value } }
        })
        val deleted = changed.change(Change(childLabel with null))
        assertEquals(listOf(2, 2), deleted { boxes }!!.map { it { children }!!.size })
        assertEquals(listOf(0, 0, 0, 0), deleted { boxes }!!.flatMap { it { children }!!.values.map { child -> child.size } })
        assertEquals(listOf("one", "two"), original { boxes }!!.map { it { label } })
    }

    @Test
    fun wildcardCollectionOperationsReachEachCompleteChild() {
        val original = TraversalRecord.create(setDefaults = false) {
            entries with linkedMapOf(1u to item("one"), 2u to item("two"))
        }
        val any = TraversalRecord.ref { entries.anyValue() }
        val numbersReference = TraversalItem.ref(any) { numbers }
        val tagsReference = TraversalItem.ref(any) { tags }
        val allocatedReference = TraversalItem.ref(any) { allocated }
        val changed = original.change(
            ListChange(numbersReference.change(deleteValues = listOf(1), addValuesToEnd = listOf(3))),
            SetChange(tagsReference.change(addValues = setOf("added"))),
            IncMapChange(allocatedReference.change(addValues = listOf("auto"))),
            IncMapAddition(IncMapKeyAdditions(allocatedReference, listOf(3u), listOf("assigned"))),
        )
        for (value in changed { entries }!!.values) {
            assertEquals(listOf(2, 3), value { numbers })
            assertEquals(setOf("kept", "added"), value { tags })
            assertEquals(mapOf(1u to "old", 2u to "auto", 3u to "assigned"), value { allocated })
        }
        for (value in original { entries }!!.values) {
            assertEquals(listOf(1, 2), value { numbers })
            assertEquals(setOf("kept"), value { tags })
            assertEquals(mapOf(1u to "old"), value { allocated })
        }
    }

    @Test
    fun inactiveTypedPathsCannotReplaceOrDeleteTheActiveCase() {
        val original = ComplexModel.create(setDefaults = false) { multi with T1("active") }
        assertFailsWith<SubObjectChangeException> {
            original.change(Change(ComplexModel.ref { multi.atType(T2) } with 3))
        }
        val inactiveChild = EmbeddedMarykModel.ref(ComplexModel.ref { multi.atType(T3) }) { value }
        for (deletion in listOf(ComplexModel.ref { multi.atType(T3) } with null, inactiveChild with null)) {
            assertEquals(original, original.change(Change(deletion)))
        }
        val supplied = EmbeddedMarykModel.create(setDefaults = false) { value with "new case" }
        val activeChild = EmbeddedMarykModel.ref(ComplexModel.ref { multi.atType(T3) }) { value }
        val changed = original.change(Change(ComplexModel.ref { multi } with T3(supplied), activeChild with "edited"))
        assertEquals("edited", changed[activeChild])
        assertEquals("new case", supplied { value })
        assertEquals(T1("active"), original { multi })
    }

    @Test
    fun wildcardDeletionSkipsInactiveCasesAndContinuesToMatchingCases() {
        val original = ComplexModel.create(setDefaults = false) {
            mapIntMulti with linkedMapOf(
                1u to T1("one"),
                2u to T3(EmbeddedMarykModel.create(setDefaults = false) { value with "two" }),
                3u to T1("three"),
                4u to T3(EmbeddedMarykModel.create(setDefaults = false) { value with "four" }),
            )
        }
        val any = ComplexModel.ref { mapIntMulti.anyValue() }
        val typed = ComplexModel.multi.definition.typedValueRef(T3, any)
        val child = EmbeddedMarykModel.ref(typed) { value }
        val changed = original.change(Change(child with null))
        assertEquals(4, changed { mapIntMulti }!!.size)
        assertEquals(T1("one"), changed { mapIntMulti }!![1u])
        assertEquals(T1("three"), changed { mapIntMulti }!![3u])
        assertEquals(T3(EmbeddedMarykModel.create(setDefaults = false) {}), changed { mapIntMulti }!![2u])
        assertEquals(T3(EmbeddedMarykModel.create(setDefaults = false) {}), changed { mapIntMulti }!![4u])
        assertEquals("two", (original { mapIntMulti }!![2u]!!.value as Values<*>)[EmbeddedMarykModel.ref { value }])
    }
}
