package maryk.core.properties.graph

import kotlinx.datetime.LocalTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.graph
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect


class RootPropRefGraphTest {
    private val graph = TestMarykModel.graph {
        listOf(
            string,
            set,
            map[LocalTime(12, 34, 56)],
            incMap[2u],
            graph(embeddedValues) {
                listOf(
                    value,
                    graph(model) {
                        listOf(value)
                    }
                )
            }
        )
    }

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun containsReference() {
        assertTrue(graph.contains(TestMarykModel { string::ref }))
        assertFalse(graph.contains(TestMarykModel { int::ref }))

        assertTrue(graph.contains(TestMarykModel { map::ref }))
        assertTrue(graph.contains(TestMarykModel { map.refAt(LocalTime(12, 34, 56)) }))
        assertFalse(graph.contains(TestMarykModel { map.refAt(LocalTime(1, 2, 3)) }))

        assertTrue(graph.contains(TestMarykModel { incMap::ref }))
        assertTrue(graph.contains(TestMarykModel { incMap.refAt(2u) }))
        assertFalse(graph.contains(TestMarykModel { incMap.refAt(3u) }))

        assertTrue(graph.contains(TestMarykModel { embeddedValues::ref }))
        assertTrue(graph.contains(TestMarykModel { embeddedValues { value::ref } }))
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
            - string
            - set
            - 'map[12:34:56]'
            - embeddedValues:
              - value
              - model:
                - value
            - incMap[2]

            """.trimIndent()
        ) {
            checkYamlConversion(this.graph, RootPropRefGraph, { this.context })
        }
    }
}
