package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeResponseTest {
    private val key = SimpleMarykObject.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val changeResponse = ChangeResponse(
        SimpleMarykObject,
        listOf(
            Success(32352L.toUInt64()),
            DoesNotExist(key),
            ValidationFail(ValidationUmbrellaException(null, listOf(
                InvalidValueException(SimpleMarykObject.ref{ value }, "wrong")
            ))),
            RequestFail("Request was wrong"),
            AuthFail(),
            ServerFail("Something went wrong")
        )
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.changeResponse, ChangeResponse, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.changeResponse, ChangeResponse, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.changeResponse, ChangeResponse, this.context) shouldBe """
        dataModel: SimpleMarykObject
        statuses:
        - !SUCCESS
          version: 0x0000000000007e60
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
