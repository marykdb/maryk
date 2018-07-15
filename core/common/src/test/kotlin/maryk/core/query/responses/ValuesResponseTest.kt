package maryk.core.query.responses

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.ValuesWithMetaData
import maryk.test.shouldBe
import kotlin.test.Test

class ValuesResponseTest {
    private val value = SimpleMarykModel(value = "haha1")

    private val key = SimpleMarykModel.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val objectsResponse = ValuesResponse(
        SimpleMarykModel,
        listOf(
            ValuesWithMetaData(
                key = key,
                values = value,
                firstVersion = 0L.toUInt64(),
                lastVersion = 14141L.toUInt64(),
                isDeleted = false
            )
        )
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.objectsResponse, ValuesResponse, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.objectsResponse, ValuesResponse, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.objectsResponse, ValuesResponse, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        values:
        - key: +1xO4zD4R5sIMcS9pXTZEA
          values:
            value: haha1
          firstVersion: 0
          lastVersion: 14141
          isDeleted: false

        """.trimIndent()
    }
}
