package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class CheckTest {
    private val valueCheck = Check(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 42
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 42

            """.trimIndent()
        ) {
            checkYamlConversion(this.valueCheck, Check, { this.context })
        }
    }
}
