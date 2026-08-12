package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.yaml.MarykYamlReader
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FilterNestingTest {
    @Test
    fun maximumNestingRoundTripsAcrossFormatsAndEvaluation() {
        val filter = nestedNot(MAX_FILTER_NESTING_DEPTH)

        checkJsonConversion(filter, Not, ::newContext)
        checkYamlConversion(filter, Not, ::newContext)
        checkProtoBufConversion(filter, Not, ::newContext)
        assertTrue(matchesFilter(filter, valueMatcher = { _, matcher -> matcher("value") }))
    }

    @Test
    fun overDepthFiltersAreRejectedAcrossFormatsAndEvaluation() {
        val filter = nestedNot(MAX_FILTER_NESTING_DEPTH + 1)

        assertFailsWith<FilterNestingDepthException> {
            checkJsonConversion(filter, Not, ::newContext)
        }
        assertFailsWith<FilterNestingDepthException> {
            checkYamlConversion(filter, Not, ::newContext)
        }
        assertFailsWith<FilterNestingDepthException> {
            checkProtoBufConversion(filter, Not, ::newContext)
        }
        assertFailsWith<FilterNestingDepthException> {
            matchesFilter(filter, valueMatcher = { _, matcher -> matcher("value") })
        }
    }

    @Test
    fun overDepthJsonAndYamlAreRejectedWhileDecoding() {
        val maximum = nestedNot(MAX_FILTER_NESTING_DEPTH)
        val json = checkJsonConversion(maximum, Not, ::newContext)
        val yaml = checkYamlConversion(maximum, Not, ::newContext)

        assertFailsWith<FilterNestingDepthException> {
            Not.Serializer.readJson("[[\"Not\",$json]]", newContext())
        }
        assertFailsWith<FilterNestingDepthException> {
            val overDepthYaml = "- !Not\n" + yaml.lines().joinToString("\n") { "  $it" }
            var index = 0
            Not.Serializer.readJson(MarykYamlReader { overDepthYaml.getOrNull(index++) }, newContext())
        }
    }

    private fun nestedNot(depth: Int): Not {
        var filter: IsFilter = Exists(SimpleMarykModel { value::ref })
        repeat(depth) {
            filter = Not(filter)
        }
        return filter as Not
    }

    private fun newContext() = RequestContext(
        mapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)),
        dataModel = SimpleMarykModel
    )
}
