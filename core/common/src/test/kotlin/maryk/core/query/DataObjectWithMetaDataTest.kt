package maryk.core.query

import maryk.Option
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.lib.time.DateTime
import maryk.test.shouldBe
import kotlin.test.Test

class DataObjectWithMetaDataTest {
    private val value = TestMarykObject(
        string = "name",
        int = 5123123,
        uint = 555.toUInt32(),
        double = 6.33,
        bool = true,
        enum = Option.V2,
        dateTime = DateTime(2017, 12, 5, 1, 33, 55)
    )

    private val key1 = TestMarykObject.key(this.value)

    private val dataObjectWithMetaData = DataObjectWithMetaData(
        key1,
        value,
        firstVersion = 12L.toUInt64(),
        lastVersion = 12345L.toUInt64(),
        isDeleted = false
    )

    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.dataObjectWithMetaData, DataObjectWithMetaData, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.dataObjectWithMetaData, DataObjectWithMetaData, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.dataObjectWithMetaData, DataObjectWithMetaData, { this.context }) shouldBe """
        key: AAACKwEBAQAC
        dataObject:
          string: name
          int: 5123123
          uint: 555
          double: 6.33
          dateTime: '2017-12-05T01:33:55'
          bool: true
          enum: V2
        firstVersion: 12
        lastVersion: 12345
        isDeleted: false

        """.trimIndent()
    }
}
