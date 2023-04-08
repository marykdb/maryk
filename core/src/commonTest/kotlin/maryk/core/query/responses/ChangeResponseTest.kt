package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.models.key
import maryk.core.query.RequestContext
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ChangeResponseTest {
    private val key = CompleteMarykModel.key("+1xO4zD4R5R5R5sEIMcS94D3dpXTZEA")

    private val changeResponse = ChangeResponse(
        CompleteMarykModel,
        listOf(
            ChangeSuccess(
                32352uL,
                listOf(
                    IncMapAddition(
                        IncMapKeyAdditions(
                            CompleteMarykModel { incMap::ref },
                            listOf(
                                22u,
                                23u
                            )
                        )
                    )
                )
            ),
            DoesNotExist(key),
            ValidationFail(
                ValidationUmbrellaException(
                    null, listOf(
                        InvalidValueException(CompleteMarykModel { string::ref }, "wrong")
                    )
                )
            ),
            RequestFail("Request was wrong"),
            AuthFail(),
            ServerFail("Something went wrong")
        )
    )

    private val context = RequestContext(mapOf(
        CompleteMarykModel.Meta.name toUnitLambda { CompleteMarykModel }
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
            dataModel: CompleteMarykModel
            statuses:
            - !CHANGE_SUCCESS
              version: 32352
              changes:
              - !IncMapAddition
                incMap:
                  addedKeys: [22, 23]
            - !DOES_NOT_EXIST
              key: +1xO4zD4R5R5R5sEIMcS94D3dpXTZEA
            - !VALIDATION_FAIL
              exceptions:
              - !INVALID_VALUE
                reference: string
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
