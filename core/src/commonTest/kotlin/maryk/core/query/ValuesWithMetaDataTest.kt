package maryk.core.query

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ValuesWithMetaDataTest {
    private val value = TestMarykModel(
        string = "name",
        int = 5123123,
        uint = 555u,
        double = 6.33,
        bool = true,
        enum = V3,
        dateTime = LocalDateTime(2017, 12, 5, 1, 33, 55)
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
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
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
        expect(
            """
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
        ) {
            checkYamlConversion(this.valuesMetaData, ValuesWithMetaData, { this.context })
        }
    }
}
