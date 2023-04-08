package maryk.core.properties.graph

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.graph
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect


class RootPropRefGraphTest {
    private val graph = TestMarykModel.graph {
        listOf(
            string,
            set,
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
            TestMarykModel.Meta.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

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
            - embeddedValues:
              - value
              - model:
                - value

            """.trimIndent()
        ) {
            checkYamlConversion(this.graph, RootPropRefGraph, { this.context })
        }
    }
}
