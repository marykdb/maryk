package maryk.core.properties.graph

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.graph
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.references.dsl.at
import maryk.core.query.RequestContext
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class MapWithEmbedPropRefGraphTest {
    private val graph = ComplexModel.graph {
        listOf(
            mapIntObject.graph(2u) {
                listOf(
                    EmbeddedMarykModel.value,
                    graph(EmbeddedMarykModel.marykModel) { listOf(TestMarykModel.string) }
                )
            }
        )
    }

    private val context = RequestContext(
        dataModels = mapOf(
            ComplexModel.Meta.name to DataModelReference(ComplexModel),
            EmbeddedMarykModel.Meta.name to DataModelReference(EmbeddedMarykModel),
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = ComplexModel
    )

    @Test
    fun containsReference() {
        assertTrue(graph.contains(ComplexModel { mapIntObject.at(2u) { EmbeddedMarykModel.value::ref } }))
        assertTrue(graph.contains(ComplexModel { mapIntObject.at(2u) { EmbeddedMarykModel.marykModel { TestMarykModel.string::ref } } }))
        assertFalse(graph.contains(ComplexModel { mapIntObject.at(3u) { EmbeddedMarykModel.value::ref } }))
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
        expect(
            """
            - mapIntObject[2]:
              - value
              - marykModel:
                - string
            """.trimIndent() + "\n"
        ) {
            checkYamlConversion(this.graph, RootPropRefGraph, { this.context })
        }
    }
}

