package maryk.core.properties.graph

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class PropRefGraphTest {
    private val graph = TestMarykModel.properties.run {
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
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.graph, PropRefGraph, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.graph, PropRefGraph, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.graph, PropRefGraph, { this.context }) shouldBe """
        embeddedValues:
        - value
        - model:
          - marykModel

        """.trimIndent()
    }
}
