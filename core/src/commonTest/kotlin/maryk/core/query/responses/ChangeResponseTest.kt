@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.responses

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.RequestContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeResponseTest {
    private val key = SimpleMarykModel.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val changeResponse = ChangeResponse(
        SimpleMarykModel,
        listOf(
            Success(32352uL),
            DoesNotExist(key),
            ValidationFail(ValidationUmbrellaException(null, listOf(
                InvalidValueException(SimpleMarykModel.ref{ value }, "wrong")
            ))),
            RequestFail("Request was wrong"),
            AuthFail(),
            ServerFail("Something went wrong")
        )
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
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
        checkYamlConversion(this.changeResponse, ChangeResponse, { this.context }) shouldBe """
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
    }
}
