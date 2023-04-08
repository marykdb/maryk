package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.Test
import kotlin.test.expect

class AddRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            to: SimpleMarykModel
            objects:
            - value: haha1
            - value: haha2

            """.trimIndent()
        ) {
            checkYamlConversion(addRequest, AddRequest, { this.context })
        }
    }
}
