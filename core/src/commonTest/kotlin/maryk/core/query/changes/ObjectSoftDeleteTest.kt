package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ObjectSoftDeleteTest {
    private val objectSoftDeleteChange = ObjectSoftDeleteChange(
        isDeleted = true
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
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
        checkYamlConversion(this.objectSoftDeleteChange, ObjectSoftDeleteChange, { this.context }) shouldBe """
        |isDeleted: true
        |""".trimMargin()
    }
}
