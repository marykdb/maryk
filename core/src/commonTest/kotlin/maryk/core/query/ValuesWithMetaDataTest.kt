package maryk.core.query

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.key
import maryk.lib.time.DateTime
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ValuesWithMetaDataTest {
    private val value = TestMarykModel(
        string = "name",
        int = 5123123,
        uint = 555u,
        double = 6.33,
        bool = true,
        enum = V3,
        dateTime = DateTime(2017, 12, 5, 1, 33, 55)
    )

    private val key1 = TestMarykModel.key(this.value)

    private val valuesMetaData = ValuesWithMetaData(
        key1,
        value,
        firstVersion = 12uL,
        lastVersion = 12345uL,
        isDeleted = false
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valuesMetaData, ValuesWithMetaData, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valuesMetaData, ValuesWithMetaData, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.valuesMetaData, ValuesWithMetaData, { this.context }) shouldBe """
        key: AAACKwEAAw
        values:
          string: name
          int: 5123123
          uint: 555
          double: 6.33
          dateTime: '2017-12-05T01:33:55'
          bool: true
          enum: V3(3)
        firstVersion: 12
        lastVersion: 12345
        isDeleted: false

        """.trimIndent()
    }
}
