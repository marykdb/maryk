package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.change
import maryk.core.query.changes.delete
import maryk.test.shouldBe
import kotlin.test.Test

class ObjectChangesResponseTest {
    private val key = SimpleMarykObject.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val objectChangesResponse = ObjectChangesResponse(
        SimpleMarykObject,
        listOf(
            key.change(
                SimpleMarykObject.ref { value }.change("hoho"),
                SimpleMarykObject.ref { value }.delete(),
                lastVersion = 14141L.toUInt64()
            )
        )
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.objectChangesResponse, ObjectChangesResponse, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.objectChangesResponse, ObjectChangesResponse, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.objectChangesResponse, ObjectChangesResponse, this.context) shouldBe """
        dataModel: SimpleMarykObject
        changes:
        - key: +1xO4zD4R5sIMcS9pXTZEA
          changes:
          - !Change
            reference: value
            newValue: hoho
          - !Delete
            reference: value
          lastVersion: 0x000000000000373d

        """.trimIndent()
    }
}
