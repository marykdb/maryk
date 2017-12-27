package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import kotlin.test.Test

class DeleteResponseTest {
    private val value = SimpleMarykObject(value = "haha1")

    private val key = SimpleMarykObject.key.getKey(this.value)

    private val deleteResponse = DeleteResponse(
            SimpleMarykObject,
            listOf(
                    Success(32352L.toUInt64()),
                    DoesNotExist(key),
                    AuthFail(),
                    ServerFail("Something went wrong")
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SimpleMarykObject.name to SimpleMarykObject
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