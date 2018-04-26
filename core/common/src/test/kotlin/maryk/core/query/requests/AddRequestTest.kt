package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class AddRequestTest {
    private val addRequest = SimpleMarykObject.add(
        SimpleMarykObject(value = "haha1"),
        SimpleMarykObject(value = "haha2")
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.addRequest, AddRequest, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.addRequest, AddRequest, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.addRequest, AddRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        objectsToAdd:
        - value: haha1
        - value: haha2

        """.trimIndent()
    }
}
