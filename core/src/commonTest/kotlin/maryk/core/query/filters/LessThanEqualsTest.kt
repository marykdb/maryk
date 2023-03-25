package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class LessThanEqualsTest {
    private val lessThanEquals = LessThanEquals(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 6
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.lessThanEquals, LessThanEquals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.lessThanEquals, LessThanEquals, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 6

            """.trimIndent()
        ) {
            checkYamlConversion(this.lessThanEquals, LessThanEquals, { this.context })
        }
    }
}
