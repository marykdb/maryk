package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DataObjectWithMetaData
import maryk.test.shouldBe
import kotlin.test.Test

class ObjectsResponseTest {
    private val value = SimpleMarykObject(value = "haha1")

    private val key = SimpleMarykObject.key("+1xO4zD4R5sIMcS9pXTZEA")

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
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.objectsResponse, ObjectsResponse, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.objectsResponse, ObjectsResponse, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.objectsResponse, ObjectsResponse, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        objects:
        - key: +1xO4zD4R5sIMcS9pXTZEA
          dataObject:
            value: haha1
          firstVersion: 0x0000000000000000
          lastVersion: 0x000000000000373d
          isDeleted: false

        """.trimIndent()
    }
}
