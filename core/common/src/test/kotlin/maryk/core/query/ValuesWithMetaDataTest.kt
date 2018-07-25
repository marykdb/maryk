package maryk.core.query

import maryk.Option
import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.lib.time.DateTime
import maryk.test.shouldBe
import kotlin.test.Test

class ValuesWithMetaDataTest {
    private val value = TestMarykModel(
        string = "name",
        int = 5123123,
        uint = 555.toUInt32(),
        double = 6.33,
        bool = true,
        enum = Option.V3,
        dateTime = DateTime(2017, 12, 5, 1, 33, 55)
    )

    private val key1 = TestMarykModel.key(this.value)

    private val valuesMetaData = ValuesWithMetaData(
        key1,
        value,
        firstVersion = 12L.toUInt64(),
        lastVersion = 12345L.toUInt64(),
        isDeleted = false
    )

    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.valuesMetaData, ValuesWithMetaData, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.valuesMetaData, ValuesWithMetaData, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.valuesMetaData, ValuesWithMetaData, { this.context }) shouldBe """
        key: AAACKwEBAQAD
        values:
          string: name
          int: 5123123
          uint: 555
          double: 6.33
          dateTime: '2017-12-05T01:33:55'
          bool: true
          enum: V3
        firstVersion: 12
        lastVersion: 12345
        isDeleted: false

        """.trimIndent()
    }
}
