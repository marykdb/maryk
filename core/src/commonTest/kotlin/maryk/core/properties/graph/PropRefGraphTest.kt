package maryk.core.properties.graph

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class PropRefGraphTest {
    private val graph = TestMarykModel.run {
        graph(embeddedValues) {
            listOf(
                value,
                graph(model) {
                    listOf(marykModel)
                }
            )
        }
    }

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun containsReference() {
        assertTrue(graph.contains(EmbeddedMarykModel { value::ref }))
        assertFalse(graph.contains(EmbeddedMarykModel { marykModel::ref }))

        assertTrue(graph.contains(EmbeddedMarykModel { model::ref }))
        assertFalse(graph.contains(EmbeddedMarykModel { model { model::ref } }))
        assertTrue(graph.contains(EmbeddedMarykModel { model { marykModel::ref } }))
        assertTrue(graph.contains(EmbeddedMarykModel { model { marykModel { string::ref } } }))
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.graph, PropRefGraph, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.graph, PropRefGraph.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            embeddedValues:
            - value
            - model:
              - marykModel

            """.trimIndent()
        ) {
            checkYamlConversion(this.graph, PropRefGraph.Model, { this.context })
        }
    }
}
