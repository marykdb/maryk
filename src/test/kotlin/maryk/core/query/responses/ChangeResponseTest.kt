package maryk.core.query.responses

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.test.Test

class ChangeResponseTest {
    private val value = SubMarykObject(value = "haha1")

    private val key = SubMarykObject.key.getKey(this.value)

    private val changeResponse = ChangeResponse(
            SubMarykObject,
            listOf(
                    Success(32352L.toUInt64()),
                    DoesNotExist(key),
                    ValidationFail(ValidationUmbrellaException(null, listOf(
                            InvalidValueException(SubMarykObject.ref{ value }, "wrong")
                    ))),
                    RequestFail("Request was wrong"),
                    AuthFail(),
                    ServerFail("Something went wrong")
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.changeResponse, ChangeResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.changeResponse, ChangeResponse, this.context)
    }
}