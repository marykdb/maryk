package maryk.core.properties.graph

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.graph
import maryk.core.properties.references.dsl.at
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SelectionIntegrityCase
import maryk.test.models.SelectionIntegrityLeaf
import maryk.test.models.SelectionIntegrityModel
import maryk.test.models.TestValueObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionIntegrityTest {
    @Test
    fun bracketedMapKeysSurviveThePublicGraphJsonRoundTrip() {
        for (key in listOf("", "a]b", "a[b", "[nested]", "dot.key", "\\key", "a]")) {
            val graph = SelectionIntegrityModel.graph { listOf(records[key]) }
            val decoded = roundTrip(graph)
            assertEquals(key, (decoded.properties.single() as GraphMapItem<*, *>).key)
        }
    }

    @Test
    fun nonComparableAndValueObjectKeysUseTheirDeclaredCodecs() {
        val point = GeoPoint(1.25, -2.5)
        val boxed = TestValueObject(3, LocalDateTime(2020, 1, 2, 3, 4), true)
        for (graph in listOf(
            SelectionIntegrityModel.graph { listOf(points[point]) },
            SelectionIntegrityModel.graph { listOf(valueKeys[boxed]) },
        )) {
            assertEquals(graph, roundTrip(graph))
            checkProtoBufConversion(graph, RootPropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
            checkYamlConversion(graph, RootPropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        }
    }

    @Test
    fun mapSelectionsUseStoredKeyIdentityAfterNormalization() {
        val requested = GeoPoint(1.234567891, -2.500000019)
        val stored = GeoPoint(requested.latitudeAsInt(), requested.longitudeAsInt())
        val source = SelectionIntegrityModel.create { points with mapOf(stored to "selected", GeoPoint(0, 0) to "other") }
        val graph = SelectionIntegrityModel.graph { listOf(points[requested]) }
        assertTrue(graph.contains(SelectionIntegrityModel.ref { points.at(stored) }))
        for (selection in listOf(graph, roundTrip(graph))) {
            assertEquals(mapOf(stored to "selected"), source.filterWithSelect(selection).get { points })
        }
    }

    @Test
    fun everyRequestedMapKeyAndNestedFieldIsSelected() {
        val graph = SelectionIntegrityModel.graph {
            listOf(records.graph("a") { listOf(first) }, records.graph("b") { listOf(second) })
        }
        assertTrue(graph.contains(SelectionIntegrityModel.ref { records.at("a") { first } }))
        assertTrue(graph.contains(SelectionIntegrityModel.ref { records.at("b") { second } }))
        assertFalse(graph.contains(SelectionIntegrityModel.ref { records.at("a") { second } }))
        assertFalse(graph.contains(SelectionIntegrityModel.ref { records.at("b") { first } }))
        assertFalse(graph.contains(SelectionIntegrityModel.ref { records.at("c") { first } }))
    }

    @Test
    fun typedSelectionsMatchTheirCaseAndCombineIndependentBranches() {
        val graph = SelectionIntegrityModel.graph {
            listOf(typed.withTypeGraph(SelectionIntegrityCase.A) { listOf(first) },
                typed.withTypeGraph(SelectionIntegrityCase.B) { listOf(second) })
        }
        assertTrue(graph.contains(SelectionIntegrityModel.ref { typed.withType(SelectionIntegrityCase.A) { first } }))
        assertTrue(graph.contains(SelectionIntegrityModel.ref { typed.withType(SelectionIntegrityCase.B) { second } }))
        assertFalse(graph.contains(SelectionIntegrityModel.ref { typed.withType(SelectionIntegrityCase.A) { second } }))
        assertFalse(graph.contains(SelectionIntegrityModel.ref { typed.withType(SelectionIntegrityCase.B) { first } }))
    }

    @Test
    fun inMemoryProjectionUsesLiteralExpectedValuesForMapsAndTypedPayloads() {
        val leaf = SelectionIntegrityLeaf.create {
            first with "visible"
            second with "hidden"
        }
        val source = SelectionIntegrityModel.create {
            records with mapOf("a" to leaf, "b" to leaf, "c" to leaf)
            typed with SelectionIntegrityCase.A(leaf)
        }
        val graph = SelectionIntegrityModel.graph {
            listOf(records.graph("a") { listOf(first) }, records.graph("b") { listOf(second) },
                typed.withTypeGraph(SelectionIntegrityCase.A) { listOf(first) })
        }
        val projected = source.filterWithSelect(graph)
        val firstOnly = SelectionIntegrityLeaf.create { first with "visible" }
        val secondOnly = SelectionIntegrityLeaf.create { second with "hidden" }
        assertEquals(mapOf("a" to firstOnly, "b" to secondOnly), projected.get { records })
        assertEquals(SelectionIntegrityCase.A(firstOnly), projected.get { typed })
        assertEquals(3, source.get { records }!!.size)
    }

    @Test
    fun replacementOperandsUseTheSameSelectionAsSnapshots() {
        val leaf = SelectionIntegrityLeaf.create {
            first with "visible"
            second with "hidden"
        }
        val firstOnly = SelectionIntegrityLeaf.create { first with "visible" }
        val source = SelectionIntegrityModel.create {
            records with mapOf("a" to leaf, "b" to leaf)
            details with leaf
            typed with SelectionIntegrityCase.A(leaf)
        }
        val select = SelectionIntegrityModel.graph {
            listOf(records.graph("a") { listOf(first) },
                graph(details) { listOf(first) },
                typed.withTypeGraph(SelectionIntegrityCase.A) { listOf(first) })
        }
        val replacement = Change(
            SelectionIntegrityModel.ref { records } with mapOf("a" to leaf, "b" to leaf),
            SelectionIntegrityModel.ref { details } with leaf,
            SelectionIntegrityModel.ref { typed } with SelectionIntegrityCase.A(leaf),
        ).filterWithSelect(select)!!
        val expected = SelectionIntegrityModel.create {
            records with mapOf("a" to firstOnly)
            details with firstOnly
            typed with SelectionIntegrityCase.A(firstOnly)
        }
        assertEquals(expected, SelectionIntegrityModel.create {}.change(replacement))
        assertEquals(expected, source.filterWithSelect(select))
        val check = Check(SelectionIntegrityModel.ref { details } with leaf)
        assertEquals(Check(SelectionIntegrityModel.ref { details } with firstOnly), check.filterWithSelect(select))
        val entry = Change(SelectionIntegrityModel.ref { records.at("a") } with leaf).filterWithSelect(select)!!
        assertEquals(mapOf("a" to firstOnly), expected.change(entry).get { records })
        assertEquals(source, source.filterWithSelect(SelectionIntegrityModel.graph { listOf(records, details, typed) }))
    }

    @Test
    fun allocatedMapUpdatesFilterKeysAndNestedValuesWithoutReallocating() {
        val nested = EmbeddedMarykModel.create { value with "hidden-child" }
        val leaf = EmbeddedMarykModel.create {
            value with "visible"
            model with nested
        }
        val select = CompleteMarykModel.graph { listOf(incMap.graph(1u) { listOf(value) }) }
        val addition = IncMapAddition(IncMapKeyAdditions(CompleteMarykModel.ref { incMap },
            listOf(1u, 2u), listOf(leaf, leaf)))
        val filtered = addition.filterWithSelect(select)!!.additions.single()
        assertEquals<Any?>(listOf(1u), filtered.addedKeys)
        assertEquals(listOf(EmbeddedMarykModel.create { value with "visible" }), filtered.addedValues)
        assertNull(addition.filterWithSelect(CompleteMarykModel.graph { listOf(incMap[3u]) }))
        val command = IncMapChange(CompleteMarykModel.ref { incMap }.change(addValues = listOf(leaf)))
        assertNull(command.filterWithSelect(select), "Partially selected maps must use resolved additions, never allocate keys again")
        val whole = CompleteMarykModel.graph { listOf(incMap) }
        assertEquals(command, command.filterWithSelect(whole))
        assertEquals(addition, addition.filterWithSelect(whole))
    }

    @Test
    fun nestedMixedSelectionsAreIndependentOfBranchOrder() {
        val nested = SelectionIntegrityLeaf.create {
            first with "nested-first"
            second with "nested-second"
        }
        val leaf = SelectionIntegrityLeaf.create {
            first with "outer-first"
            second with "outer-second"
            child with nested
        }
        val source = SelectionIntegrityModel.create {
            records with mapOf("a]b" to leaf, "whole" to leaf, "excluded" to leaf)
            typed with SelectionIntegrityCase.A(leaf)
        }
        val graph = SelectionIntegrityModel.graph {
            listOf(records.graph("a]b") { listOf(graph(child) { listOf(first) }) },
                records.graph("a]b") { listOf(second) }, records["whole"],
                typed.withTypeGraph(SelectionIntegrityCase.A) { listOf(graph(child) { listOf(second) }) })
        }
        val expected = SelectionIntegrityModel.create {
            records with mapOf("a]b" to SelectionIntegrityLeaf.create {
                second with "outer-second"
                child with SelectionIntegrityLeaf.create { first with "nested-first" }
            }, "whole" to leaf)
            typed with SelectionIntegrityCase.A(SelectionIntegrityLeaf.create {
                child with SelectionIntegrityLeaf.create { second with "nested-second" }
            })
        }
        for (seed in 0 until 12) {
            val reordered = SelectionIntegrityModel.graph { graph.properties.shuffled(Random(seed)) }
            for (selection in listOf(reordered, roundTrip(reordered))) {
                val result = source.filterWithSelect(selection)
                assertEquals(expected, result, "branch permutation $seed")
                assertEquals(expected, result.filterWithSelect(selection), "projection is idempotent")
            }
        }
        checkProtoBufConversion(graph, RootPropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        checkYamlConversion(graph, RootPropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
    }

    @Test
    fun directGraphDecodersRoundTripMixedChildren() {
        val embedded = SelectionIntegrityModel.graph(SelectionIntegrityModel.details) {
            listOf(children["a]b"], graph(child) { listOf(first) })
        }
        checkJsonConversion(embedded, PropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        checkYamlConversion(embedded, PropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        checkProtoBufConversion(embedded, PropRefGraph, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        val mapped = SelectionIntegrityModel.records.graph("a]b") {
            listOf(children["x[y]"], graph(child) { listOf(second) })
        }
        checkJsonConversion(mapped, GraphMapItem, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        checkYamlConversion(mapped, GraphMapItem, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
        checkProtoBufConversion(mapped, GraphMapItem, { GraphContext(SelectionIntegrityModel) }, resetContextBeforeRead = true)
    }

    @Test
    fun decodedSelectionsNormalizeUnsortedFieldIndexesAtEveryLevel() {
        val json = """[{"details":["second","first"]},{"records[b]":["second","first"]}]"""
        val graph = RootPropRefGraph.Serializer.readJson(json, GraphContext(SelectionIntegrityModel)).toDataObject()
        assertEquals(listOf(1u, 4u), graph.properties.map { it.index })
        assertEquals(listOf(1u, 2u), (graph.properties.first() as GraphMapItem<*, *>).properties.map { it.index })
        assertEquals(listOf(1u, 2u), (graph.properties.last() as PropRefGraph<*, *>).properties.map { it.index })
    }

    @Test
    fun nonlocalReferencesCannotSilentlySelectADifferentRootProperty() {
        for (document in listOf(
            """["details.first"]""",
            """[{"details.child":["first"]}]""",
            """["details.children[a]"]""",
            """[{"details.children[a]":["first"]}]""",
        )) {
            assertFails(document) {
                RootPropRefGraph.Serializer.readJson(JsonReader(document), GraphContext(SelectionIntegrityModel)).toDataObject()
            }
        }
    }

    private fun roundTrip(graph: RootPropRefGraph<SelectionIntegrityModel>): RootPropRefGraph<*> {
        val output = StringBuilder()
        RootPropRefGraph.Serializer.writeObjectAsJson(graph, JsonWriter(writer = output::append), GraphContext(SelectionIntegrityModel))
        return RootPropRefGraph.Serializer.readJson(JsonReader(output.toString()), GraphContext(SelectionIntegrityModel)).toDataObject()
    }
}
