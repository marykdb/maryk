package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DataObjectWithMetaData
import kotlin.test.Test

class ObjectsResponseTest {
    private val value = SimpleMarykObject(value = "haha1")

    private val key = SimpleMarykObject.key.getKey(this.value)

    private val objectsResponse = ObjectsResponse(
            SimpleMarykObject,
            listOf(
                    DataObjectWithMetaData(
                            key = key,
                            dataObject = value,
                            firstVersion = 0L.toUInt64(),
                            lastVersion = 14141L.toUInt64(),
                            isDeleted = false
                    )
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.objectsResponse, ObjectsResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.objectsResponse, ObjectsResponse, this.context)
    }
}