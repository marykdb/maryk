package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class MultiTypeChangeTest {
    val ref = TestMarykModel.ref { multi }

    private val multiTypeChange = MultiTypeChange(
        TestMarykModel.ref { multi } with V3
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel,
        reference = TestMarykModel.ref { multi }
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.multiTypeChange, MultiTypeChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.multiTypeChange, MultiTypeChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.multiTypeChange, MultiTypeChange, { this.context }) shouldBe """
        multi: V3

        """.trimIndent()
    }
}