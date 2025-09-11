package maryk.core.properties.graph

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.graph
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiTypePropRefGraphTest {
    private val graph = ComplexModel.graph {
        listOf(
            multi.withTypeGraph(MarykTypeEnum.T3) {
                listOf(
                    value,
                    graph(model) { listOf(marykModel) }
                )
            }
        )
    }

    private val context = RequestContext(
        dataModels = mapOf(
            ComplexModel.Meta.name to DataModelReference(ComplexModel)
        ),
        dataModel = ComplexModel
    )

    @Test
    fun containsReference() {
        assertTrue(graph.contains(ComplexModel { multi.withType(MarykTypeEnum.T3) { value::ref } }))
        assertTrue(graph.contains(ComplexModel { multi.withType(MarykTypeEnum.T3) { model { marykModel::ref } } }))
        assertFalse(graph.contains(ComplexModel { multi.withType(MarykTypeEnum.T3) { model { value::ref } } }))
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.graph, RootPropRefGraph, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.graph, RootPropRefGraph, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.graph, RootPropRefGraph, { this.context })
    }
}
