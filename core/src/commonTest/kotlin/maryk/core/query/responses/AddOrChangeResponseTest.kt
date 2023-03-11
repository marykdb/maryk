package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class AddOrChangeResponseTest {
    private val key = SimpleMarykModel.key("T/sdrQBeRnYrRo1h7uhfQg")

    private val addOrChangeResponse = AddOrChangeResponse(
        SimpleMarykModel,
        listOf(
            AddSuccess(
                key, 32352uL, listOf(
                    Change(SimpleMarykModel { value::ref } with "new")
                )
            ),
            AlreadyExists(key),
            ValidationFail(
                ValidationUmbrellaException(
                    null, listOf(
                        InvalidValueException(SimpleMarykModel { value::ref }, "wrong")
                    )
                )
            ),
            AuthFail(),
            ServerFail("Something went wrong"),
            ChangeSuccess(
                32352uL,
                listOf(
                    Change(
                        SimpleMarykModel { value::ref } with "new value"
                    )
                )
            ),
            DoesNotExist(key)
        )
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.addOrChangeResponse, AddOrChangeResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.addOrChangeResponse, AddOrChangeResponse.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
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
            - !CHANGE_SUCCESS
              version: 32352
              changes:
              - !Change
                value: new value
            - !DOES_NOT_EXIST
              key: T/sdrQBeRnYrRo1h7uhfQg

            """.trimIndent()
        ) {
            checkYamlConversion(this.addOrChangeResponse, AddOrChangeResponse.Model, { this.context })
        }
    }
}
