package maryk.core.query.filters

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class ValueInTest {
    private val valueIn = ValueIn(
        TestMarykObject.ref { string } with setOf("t1", "t2", "t3"),
        TestMarykObject.ref { int } with setOf(1, 2, 3)
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.valueIn, ValueIn, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.valueIn, ValueIn, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.valueIn, ValueIn, { this.context }) shouldBe """
        string: [t1, t2, t3]
        int: [1, 2, 3]

        """.trimIndent()
    }
}
