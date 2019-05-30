package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.RequestContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ChangeResponseTest {
    private val key = SimpleMarykModel.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val changeResponse = ChangeResponse(
        SimpleMarykModel,
        listOf(
            Success(32352uL),
            DoesNotExist(key),
            ValidationFail(
                ValidationUmbrellaException(
                    null, listOf(
                        InvalidValueException(SimpleMarykModel { value::ref }, "wrong")
                    )
                )
            ),
            RequestFail("Request was wrong"),
            AuthFail(),
            ServerFail("Something went wrong")
        )
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.changeResponse, ChangeResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.changeResponse, ChangeResponse, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            dataModel: SimpleMarykModel
            statuses:
            - !SUCCESS
              version: 32352
            - !DOES_NOT_EXIST
              key: +1xO4zD4R5sIMcS9pXTZEA
            - !VALIDATION_FAIL
              exceptions:
              - !INVALID_VALUE
                reference: value
                value: wrong
            - !REQUEST_FAIL
              reason: Request was wrong
            - !AUTH_FAIL
            - !SERVER_FAIL
              reason: Something went wrong

            """.trimIndent()
        ) {
            checkYamlConversion(this.changeResponse, ChangeResponse, { this.context })
        }
    }
}
