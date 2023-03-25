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

class ValueInTest {
    private val valueIn = ValueIn(
        TestMarykModel { string::ref } with setOf("t1", "t2", "t3"),
        TestMarykModel { int::ref } with setOf(1, 2, 3)
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueIn, ValueIn, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueIn, ValueIn, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: [t1, t2, t3]
            int: [1, 2, 3]

            """.trimIndent()
        ) {
            checkYamlConversion(this.valueIn, ValueIn, { this.context })
        }
    }
}
