package maryk.core.properties.graph

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class PropRefGraphTest {
    private val graph = TestMarykObject.properties.embeddedObject.props {
        it.graph(
            value,
            model.props {
                it.graph(
                    marykModel
                )
            }
        )
    }

    private val context = DataModelPropertyContext(
        dataModels = mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject
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
        embeddedObject:
        - value
        - model:
          - marykModel

        """.trimIndent()
    }
}
