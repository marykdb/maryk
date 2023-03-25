package maryk.core.query.filters

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class LessThanTest {
    private val lessThan = LessThan(
        TestMarykModel { int::ref } with 2,
        TestMarykModel { dateTime::ref } with LocalDateTime(2018, 1, 1, 13, 22, 34)
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.lessThan, LessThan, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.lessThan, LessThan, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            int: 2
            dateTime: '2018-01-01T13:22:34'

            """.trimIndent()
        ) {
            checkYamlConversion(this.lessThan, LessThan, { this.context })
        }
    }
}
