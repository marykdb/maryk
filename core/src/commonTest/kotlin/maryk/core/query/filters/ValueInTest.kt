package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ValueInTest {
    private val valueIn = ValueIn(
        TestMarykModel { string::ref } with setOf("t1", "t2", "t3"),
        TestMarykModel { int::ref } with setOf(1, 2, 3)
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
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
        checkYamlConversion(this.valueIn, ValueIn, { this.context }) shouldBe """
        string: [t1, t2, t3]
        int: [1, 2, 3]

        """.trimIndent()
    }
}
