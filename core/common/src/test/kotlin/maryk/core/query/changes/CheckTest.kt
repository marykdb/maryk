package maryk.core.query.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class CheckTest {
    private val valueCheck = Check(
        TestMarykObject.ref { string } with "test",
        TestMarykObject.ref { int } with 42
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
        checkProtoBufConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.valueCheck, Check, { this.context }) shouldBe """
        string: test
        int: 42

        """.trimIndent()
    }
}
