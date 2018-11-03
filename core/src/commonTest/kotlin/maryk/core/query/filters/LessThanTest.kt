package maryk.core.query.filters

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.lib.time.DateTime
import maryk.test.shouldBe
import kotlin.test.Test

class LessThanTest {
    private val lessThan = LessThan(
        TestMarykModel.ref { int } with 2,
        TestMarykModel.ref { dateTime } with DateTime(2018, 1, 1, 13, 22, 34)
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.lessThan, LessThan, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.lessThan, LessThan, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.lessThan, LessThan, { this.context }) shouldBe """
        int: 2
        dateTime: '2018-01-01T13:22:34'

        """.trimIndent()
    }
}
