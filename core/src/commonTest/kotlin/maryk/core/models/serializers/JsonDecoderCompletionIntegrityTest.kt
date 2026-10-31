package maryk.core.models.serializers

import kotlinx.datetime.LocalDate
import maryk.core.models.IsObjectDataModel
import maryk.core.models.graph
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.graph.GraphContext
import maryk.core.properties.graph.GraphMapItem
import maryk.core.properties.graph.PropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.graph.TypePropRefGraph
import maryk.core.properties.graph.graph
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Order
import maryk.core.query.pairs.with
import maryk.core.query.pairs.withType
import maryk.core.query.requests.CollectRequest
import maryk.core.yaml.MarykYamlReader
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SelectionIntegrityCase
import maryk.test.models.SelectionIntegrityModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.TestMarykModel
import maryk.test.requests.collectRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonDecoderCompletionIntegrityTest {
    @Test
    fun everyReferenceMappedMutationRequiresCompleteInnerAndOuterObjects() {
        checkPrefixes("ListChange", ListChange.Serializer,
            ListChange(TestMarykModel.ref { listOfString }.change(addValuesToEnd = listOf("one", "two"))), ::testContext)
        checkPrefixes("SetChange", SetChange.Serializer,
            SetChange(TestMarykModel.ref { set }.change(addValues = setOf(LocalDate(2020, 1, 2)))), ::testContext)
        val leaf = EmbeddedMarykModel.create { value with "one" }
        checkPrefixes("IncMapChange", IncMapChange.Serializer,
            IncMapChange(CompleteMarykModel.ref { incMap }.change(addValues = listOf(leaf))), ::completeContext)
        checkPrefixes("IncMapAddition", IncMapAddition.Serializer,
            IncMapAddition(IncMapKeyAdditions(CompleteMarykModel.ref { incMap }, listOf(1u), listOf(leaf))), ::completeContext)
    }

    @Test
    fun referencePairChangesAndFiltersRequireCompleteObjects() {
        val pair = TestMarykModel.ref { string } with "one"
        checkPrefixes("Change", Change.Serializer, Change(pair), ::testContext)
        checkPrefixes("Check", Check.Serializer, Check(pair), ::testContext)
        checkPrefixes("MultiTypeChange", MultiTypeChange.Serializer,
            MultiTypeChange(TestMarykModel.ref { multi } withType SimpleMarykTypeEnum.S3), ::testContext)
        checkPrefixes("Equals", Equals.Serializer, Equals(pair), ::testContext)
        checkPrefixes("GreaterThan", GreaterThan.Serializer, GreaterThan(pair), ::testContext)
        checkPrefixes("GreaterThanEquals", GreaterThanEquals.Serializer, GreaterThanEquals(pair), ::testContext)
        checkPrefixes("LessThan", LessThan.Serializer, LessThan(pair), ::testContext)
        checkPrefixes("LessThanEquals", LessThanEquals.Serializer, LessThanEquals(pair), ::testContext)
        checkPrefixes("Prefix", Prefix.Serializer, Prefix(pair), ::testContext)
        checkPrefixes("RegEx", RegEx.Serializer, RegEx(TestMarykModel.ref { string } with Regex("a.*")), ::testContext)
        checkPrefixes("ValueIn", ValueIn.Serializer, ValueIn(TestMarykModel.ref { string } with setOf("one", "two")), ::testContext)
        checkPrefixes("Range", Range.Serializer, Range(TestMarykModel.ref { string } with ValueRange("one", "two")), ::testContext)
    }

    @Test
    fun delegatingFilterDecodersRetainTheirContainerChecks() {
        val equals = Equals(TestMarykModel.ref { string } with "one")
        checkPrefixes("And", And.Serializer, And(equals), ::testContext)
        checkPrefixes("Or", Or.Serializer, Or(equals), ::testContext)
        checkPrefixes("Not", Not.Serializer, Not(equals), ::testContext)
        checkPrefixes("ExistsSingle", Exists.Serializer, Exists(TestMarykModel.ref { string }), ::testContext)
        checkPrefixes("ExistsMultiple", Exists.Serializer,
            Exists(TestMarykModel.ref { string }, TestMarykModel.ref { int }), ::testContext)
    }

    @Test
    fun namedRequestsRequireTheirOuterObjectToClose() {
        fun context() = RequestContext(mapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)))
        checkPrefixes("CollectRequest", CollectRequest.Serializer, collectRequest, ::context)
        val document = StringBuilder().also { output ->
            CollectRequest.Serializer.writeObjectAsJson(collectRequest, JsonWriter(writer = output::append), context())
        }.toString()
        val extraField = document.dropLast(1) + ",\"ignored\":null}"
        assertTrue(runCatching { CollectRequest.Serializer.readJson(JsonReader(extraField), context()) }.isFailure,
            "A named request must not silently discard a second field")
    }

    @Test
    fun yamlOrdersRequireAnEmptyCompleteMappingForTheirDefaultForm() {
        val control = Order.Model.Serializer.readJson(MarykYamlReader("!Desc {}"), testContext()).toDataObject()
        assertEquals(Order(direction = Direction.DESC), control)
        for (document in listOf("{", "!Desc {", "{direction: DESC}", "!Desc {field: value}")) {
            assertTrue(runCatching { Order.Model.Serializer.readJson(MarykYamlReader(document), testContext()) }.isFailure,
                "Order accepted an incomplete or nonempty default mapping: $document")
        }
    }

    @Test
    fun everyGraphDecoderRequiresAllItsClosingTokens() {
        val embedded = SelectionIntegrityModel.graph(SelectionIntegrityModel.details) { listOf(first, second) }
        val typed = SelectionIntegrityModel.typed.withTypeGraph(SelectionIntegrityCase.A) { listOf(first, second) }
        val mapped = SelectionIntegrityModel.records.graph("entry") { listOf(first, second) }
        val root = SelectionIntegrityModel.graph { listOf(embedded, typed, mapped) }
        checkPrefixes("RootGraph", RootPropRefGraph.Serializer, root, ::graphContext)
        checkPrefixes("EmbeddedGraph", PropRefGraph.Serializer, embedded, ::graphContext)
        checkPrefixes("TypeGraph", TypePropRefGraph.Serializer, typed, ::graphContext)
        checkPrefixes("MapGraph", GraphMapItem.Serializer, mapped, ::graphContext)
    }

    private fun testContext() = RequestContext(mapOf(TestMarykModel.Meta.name to DataModelReference(TestMarykModel)), TestMarykModel)
    private fun completeContext() = RequestContext(mapOf(CompleteMarykModel.Meta.name to DataModelReference(CompleteMarykModel)), CompleteMarykModel)
    private fun graphContext() = GraphContext(SelectionIntegrityModel)

    private fun <DO : Any, DM : IsObjectDataModel<DO>, CX : IsPropertyContext> checkPrefixes(
        name: String,
        serializer: IsObjectDataModelSerializer<DO, DM, *, CX>,
        value: DO,
        context: () -> CX,
    ) {
        fun encode(item: DO) = StringBuilder().also { output ->
            serializer.writeObjectAsJson(item, JsonWriter(writer = output::append), context())
        }.toString()
        val document = encode(value)
        val decoded = serializer.readJson(JsonReader(document), context()).toDataObject()
        assertEquals(document, encode(decoded), "$name complete control")
        for (length in 0 until document.length) {
            val result = runCatching { serializer.readJson(JsonReader(document.substring(0, length)), context()) }
            assertTrue(result.isFailure, "$name accepted incomplete prefix $length/${document.length}")
        }
    }
}
