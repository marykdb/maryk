package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.PropertyChange
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.test.Test

class AddResponseTest {
    private val value = SimpleMarykObject(value = "haha1")

    private val key = SimpleMarykObject.key.getKey(this.value)

    private val addResponse = AddResponse(
            SimpleMarykObject,
            listOf(
                    AddSuccess(
                            key, 32352L.toUInt64(), listOf(
                                    PropertyChange(SimpleMarykObject.ref{ value }, "new")
                            )
                    ),
                    AlreadyExists(key),
                    ValidationFail(ValidationUmbrellaException(null, listOf(
                            InvalidValueException(SimpleMarykObject.ref{ value }, "wrong")
                    ))),
                    AuthFail(),
                    ServerFail("Something went wrong")
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.addResponse, AddResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.addResponse, AddResponse, this.context)
    }
}