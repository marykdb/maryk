package maryk.core.properties.graph

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test



class RootPropRefGraphTest {
    private val graph = TestMarykObject.props {
        RootPropRefGraph(
            string,
            set,
            embeddedObject.props {
                it.graph(
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

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        dataModels = mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, ObjectPropertyDefinitions<Any>>
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
        - embeddedObject:
          - value
          - model:
            - value

        """.trimIndent()
    }
}
