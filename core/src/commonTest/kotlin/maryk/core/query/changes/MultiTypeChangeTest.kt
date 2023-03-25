package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.withType
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MultiTypeChangeTest {
    val ref = TestMarykModel { multi::ref }

    private val multiTypeChange = MultiTypeChange(
        TestMarykModel { multi::ref } withType S3
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel,
        reference = TestMarykModel { multi::ref }
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
        expect(
            """
            multi: S3

            """.trimIndent()
        ) {
            checkYamlConversion(this.multiTypeChange, MultiTypeChange, { this.context })
        }
    }
}
