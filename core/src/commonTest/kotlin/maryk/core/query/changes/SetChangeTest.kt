package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.lib.time.Date
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class SetChangeTest {
    private val setPropertyChange = SetChange(
        TestMarykModel { set::ref }.change(
            addValues = setOf(
                Date(2014, 4, 14),
                Date(2013, 3, 13)
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.setPropertyChange, SetChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.setPropertyChange, SetChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.setPropertyChange, SetChange, { this.context }) shouldBe """
        set:
          addValues: [2014-04-14, 2013-03-13]

        """.trimIndent()
    }
}
