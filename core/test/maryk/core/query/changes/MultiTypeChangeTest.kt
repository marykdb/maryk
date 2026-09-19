package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.withType
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MultiTypeChangeTest {
    val ref = TestMarykModel.ref { multi }

    private val multiTypeChange = MultiTypeChange(
        TestMarykModel.ref { multi } withType S3
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
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
        expect(
            """
            multi: S3

            """.trimIndent()
        ) {
            checkYamlConversion(this.multiTypeChange, MultiTypeChange, { this.context })
        }
    }
}
