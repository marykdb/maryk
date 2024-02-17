package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class RangeTest {
    private val range = Range(
        TestMarykModel { string::ref } with ValueRange(
            from = "test",
            to = "test999",
            inclusiveFrom = false,
            inclusiveTo = false
        ),
        TestMarykModel { int::ref } with 3..5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.range, Range, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.range, Range, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: [!Exclude test, !Exclude test999]
            int: [3, 5]

            """.trimIndent()
        ) {
            checkYamlConversion(this.range, Range, { this.context })
        }
    }
}
