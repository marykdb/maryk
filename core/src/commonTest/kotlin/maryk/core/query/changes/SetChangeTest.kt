package maryk.core.query.changes

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.lib.time.Date
import maryk.test.shouldBe
import kotlin.test.Test

class SetChangeTest {
    private val setPropertyChange = SetChange(
        TestMarykModel.ref { set }.change(
            addValues = setOf(
                Date(2014, 4, 14),
                Date(2013, 3, 13)
            ),
            deleteValues = setOf(Date(2018, 7, 17))
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.setPropertyChange, SetChange, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.setPropertyChange, SetChange, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.setPropertyChange, SetChange, { this.context }) shouldBe """
        set:
          addValues: [2014-04-14, 2013-03-13]
          deleteValues: [2018-07-17]

        """.trimIndent()
    }
}
