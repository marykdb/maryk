package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ObjectSoftDeleteTest {
    private val objectSoftDeleteChange = ObjectSoftDeleteChange(
        isDeleted = true
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
        ),
        dataModel = SimpleMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.objectSoftDeleteChange, ObjectSoftDeleteChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.objectSoftDeleteChange, ObjectSoftDeleteChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            isDeleted: true

            """.trimIndent()
        ) {
            checkYamlConversion(this.objectSoftDeleteChange, ObjectSoftDeleteChange, { this.context })
        }
    }
}
