package maryk.core.properties.graph

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test


class RootPropRefGraphTest {
    private val graph = TestMarykModel.props {
        RootPropRefGraph(
            string,
            set,
            embeddedValues.props { embeddedValuesProps ->
                embeddedValuesProps.graph(
                    value,
                    model.props {
                        it.graph(
                            value
                        )
                    }
                )
            }
        )
    }

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
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
        checkYamlConversion(this.graph, RootPropRefGraph, { this.context }) shouldBe """
        - string
        - set
        - embeddedValues:
          - value
          - model:
            - value

        """.trimIndent()
    }
}
