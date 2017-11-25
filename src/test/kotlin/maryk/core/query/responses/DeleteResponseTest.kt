package maryk.core.query.responses

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import kotlin.test.Test

class DeleteResponseTest {
    private val value = SubMarykObject(value = "haha1")

    private val key = SubMarykObject.key.getKey(this.value)

    private val deleteResponse = DeleteResponse(
            SubMarykObject,
            listOf(
                    Success(32352L.toUInt64()),
                    DoesNotExist(key),
                    AuthFail(),
                    ServerFail("Something went wrong")
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.deleteResponse, DeleteResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.deleteResponse, DeleteResponse, this.context)
    }
}