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

class GreaterThanEqualsTest {
    private val greaterThanEquals = GreaterThanEquals(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 6
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 6

            """.trimIndent()
        ) {
            checkYamlConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
        }
    }
}
