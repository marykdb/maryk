package maryk.core.query.responses

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.PropertyChange
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ServerFail
import kotlin.test.Test

class AddResponseTest {
    private val value = SubMarykObject(value = "haha1")

    private val key = SubMarykObject.key.getKey(this.value)

    private val addResponse = AddResponse(
            SubMarykObject,
            listOf(
                    AddSuccess(
                            key, 32352L.toUInt64(), listOf(
                                    PropertyChange(SubMarykObject.Properties.value.getRef(), "new")
                            )
                    ),
                    AlreadyExists(key),
                    AuthFail(),
                    ServerFail("Something went wrong")
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
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