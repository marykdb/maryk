package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.key
import maryk.core.query.RequestContext
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class RemovalUpdateTest {
    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")

    private val removalUpdate = RemovalUpdate(
        key = key,
        version = 1uL,
        reason = NotInRange
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.removalUpdate, RemovalUpdate, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.removalUpdate, RemovalUpdate, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            key: 0ruQCs38S2QaByYof+IJgA
            version: 1
            reason: NotInRange

            """.trimIndent()
        ) {
            checkYamlConversion(this.removalUpdate, RemovalUpdate, { this.context })
        }
    }
}
