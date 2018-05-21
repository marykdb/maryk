package maryk.core.query.filters

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.lib.time.DateTime
import maryk.test.shouldBe
import kotlin.test.Test

class LessThanTest {
    private val lessThen = TestMarykObject.ref { string } lessThan "test"

    private val lessThanMultiple = LessThan(
        TestMarykObject.ref { int } with 2,
        TestMarykObject.ref { dateTime } with DateTime(2018, 1, 1, 13, 22, 34)
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.lessThen, LessThan, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.lessThen, LessThan, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.lessThanMultiple, LessThan, this.context) shouldBe """
        int: 2
        dateTime: '2018-01-01T13:22:34'

        """.trimIndent()

        checkYamlConversion(this.lessThen, LessThan, this.context) shouldBe """
        string: test

        """.trimIndent()
    }
}
