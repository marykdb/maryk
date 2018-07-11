package maryk.core.query.filters

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.ValueRange
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class RangeTest {
    private val range = Range(
        TestMarykObject.ref { string } with ValueRange(
            from = "test",
            to = "test999",
            inclusiveFrom = false,
            inclusiveTo = false
        ),
        TestMarykObject.ref { int } with 3..5
    )

    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.range, Range, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.range, Range, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.range, Range, { this.context }) shouldBe """
        string: [!Exclude test, !Exclude test999]
        int: [3, 5]

        """.trimIndent()
    }
}
