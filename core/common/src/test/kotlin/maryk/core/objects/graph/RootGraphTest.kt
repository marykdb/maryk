package maryk.core.objects.graph

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test



class RootGraphTest {
    private val graph = TestMarykObject.props {
        RootGraph(
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
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.graph, RootGraph, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.graph, RootGraph, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.graph, RootGraph, { this.context }) shouldBe """
        - string
        - set
        - embeddedObject:
          - value
          - model:
            - value

        """.trimIndent()
    }
}
