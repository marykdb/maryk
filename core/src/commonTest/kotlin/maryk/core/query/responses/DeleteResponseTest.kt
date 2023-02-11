package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DeleteResponseTest {
    private val key = SimpleMarykModel.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val deleteResponse = DeleteResponse(
        SimpleMarykModel.Model,
        listOf(
            DeleteSuccess(32352uL),
            DoesNotExist(key),
            AuthFail(),
            ServerFail("Something went wrong")
        )
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.deleteResponse, DeleteResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.deleteResponse, DeleteResponse, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            dataModel: SimpleMarykModel
            statuses:
            - !DELETE_SUCCESS
              version: 32352
            - !DOES_NOT_EXIST
              key: +1xO4zD4R5sIMcS9pXTZEA
            - !AUTH_FAIL
            - !SERVER_FAIL
              reason: Something went wrong

            """.trimIndent()
        ) {
            checkYamlConversion(this.deleteResponse, DeleteResponse, { this.context })
        }
    }
}
