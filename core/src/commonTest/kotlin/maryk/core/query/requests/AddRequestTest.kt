package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.shouldBe
import kotlin.test.Test

class AddRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(addRequest, AddRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        objectsToAdd:
        - value: haha1
        - value: haha2

        """.trimIndent()
    }
}
