package maryk.core.properties.graph

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.RequestContext
import maryk.test.shouldBe
import kotlin.test.Test

class PropRefGraphTest {
    private val graph = TestMarykModel.properties.embeddedValues.props { props ->
        props.graph(
            value,
            model.props {
                it.graph(
                    marykModel
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
        checkProtoBufConversion(this.graph, PropRefGraph, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.graph, PropRefGraph, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.graph, PropRefGraph, { this.context }) shouldBe """
        embeddedValues:
        - value
        - model:
          - marykModel

        """.trimIndent()
    }
}
