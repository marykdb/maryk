package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

internal val addRequest = SimpleMarykObject.add(
    SimpleMarykObject(value = "haha1"),
    SimpleMarykObject(value = "haha2")
)

class AddRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(addRequest, AddRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        objectsToAdd:
        - value: haha1
        - value: haha2

        """.trimIndent()
    }
}
