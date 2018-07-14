package maryk.core.query.responses

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.shouldBe
import kotlin.test.Test

class AddResponseTest {
    private val key = SimpleMarykModel.key("T/sdrQBeRnYrRo1h7uhfQg")

    private val addResponse = AddResponse(
        SimpleMarykModel,
        listOf(
            AddSuccess(
                key, 32352L.toUInt64(), listOf(
                    Change(SimpleMarykModel.ref{ value } with "new")
                )
            ),
            AlreadyExists(key),
            ValidationFail(ValidationUmbrellaException(null, listOf(
                InvalidValueException(SimpleMarykModel.ref{ value }, "wrong")
            ))),
            AuthFail(),
            ServerFail("Something went wrong")
        )
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.addResponse, AddResponse, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.addResponse, AddResponse, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.addResponse, AddResponse, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        statuses:
        - !ADD_SUCCESS
          key: T/sdrQBeRnYrRo1h7uhfQg
          version: 32352
          changes:
          - !Change
            value: new
        - !ALREADY_EXISTS
          key: T/sdrQBeRnYrRo1h7uhfQg
        - !VALIDATION_FAIL
          exceptions:
          - !INVALID_VALUE
            reference: value
            value: wrong
        - !AUTH_FAIL
        - !SERVER_FAIL
          reason: Something went wrong

        """.trimIndent()
    }
}
