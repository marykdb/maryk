package maryk.core.properties.graph

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.RequestContext
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
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.graph, RootPropRefGraph, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.graph, RootPropRefGraph, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
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
