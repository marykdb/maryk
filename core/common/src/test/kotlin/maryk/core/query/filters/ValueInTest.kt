package maryk.core.query.filters

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class ValueInTest {
    private val valueIn = ValueIn(
        TestMarykModel.ref { string } with setOf("t1", "t2", "t3"),
        TestMarykModel.ref { int } with setOf(1, 2, 3)
    )

    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
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
