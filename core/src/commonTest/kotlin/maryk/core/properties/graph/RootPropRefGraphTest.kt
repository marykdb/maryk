package maryk.core.properties.graph

import kotlinx.datetime.LocalTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.graph
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect


class RootPropRefGraphTest {
    @Test
    fun rootScalarSubclassesRetainTheirCustomReferencePaths() {
        val selected = TestMarykModel.graph { listOf(string) }
        assertFalse(selected.contains(alias(TestMarykModel.string.ref(), TestMarykModel.int.ref())))
        val unselected = TestMarykModel.graph { listOf(int) }
        assertTrue(unselected.contains(alias(TestMarykModel.string.ref(), TestMarykModel.int.ref())))
    }

    private fun <T : Any, TO : Any, D : FlexBytesDefinitionWrapper<T, TO, *, *, *>, P : AnyPropertyReference> alias(
        reference: ValueWithFlexBytesPropertyReference<T, TO, D, P>,
        target: AnyPropertyReference,
    ): ValueWithFlexBytesPropertyReference<T, TO, D, P> =
        object : ValueWithFlexBytesPropertyReference<T, TO, D, P>(reference.propertyDefinition, reference.parentReference) {
            override fun unwrap(listToUse: MutableList<IsPropertyReference<*, *, *>>?): List<IsPropertyReference<*, *, in Any>> =
                target.unwrap(listToUse)
        }

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
        assertTrue(graph.contains(TestMarykModel.ref { string }))
        assertFalse(graph.contains(TestMarykModel.ref { int }))

        assertTrue(graph.contains(TestMarykModel.ref { map }))
        assertTrue(graph.contains(TestMarykModel.ref { map.at(LocalTime(12, 34, 56)) }))
        assertFalse(graph.contains(TestMarykModel.ref { map.at(LocalTime(1, 2, 3)) }))

        assertTrue(graph.contains(TestMarykModel.ref { incMap }))
        assertTrue(graph.contains(TestMarykModel.ref { incMap.at(2u) }))
        assertFalse(graph.contains(TestMarykModel.ref { incMap.at(3u) }))

        assertTrue(graph.contains(TestMarykModel.ref { embeddedValues }))
        assertTrue(graph.contains(TestMarykModel.ref { embeddedValues { value } }))
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
