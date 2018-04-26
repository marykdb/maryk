package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class RangeTest {
    private val range = TestMarykObject.ref { int } inRange 2..6

    private val range2 = SimpleMarykObject.ref { value }.inRange(
        from = "test",
        to = "test999",
        inclusiveFrom = true,
        inclusiveTo = false
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Suppress("UNCHECKED_CAST")
    private val context2 = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to SimpleMarykObject
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.range, Range, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.range, Range, this.context)
    }

    @Test
    fun convert_to_ProtoBuf_and_back2() {
        checkProtoBufConversion(this.range2, Range, this.context2)
    }

    @Test
    fun convert_to_JSON_and_back2() {
        checkJsonConversion(this.range2, Range, this.context2)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.range, Range, this.context) shouldBe """
        reference: int
        from: 2
        to: 6
        inclusiveFrom: true
        inclusiveTo: true

        """.trimIndent()
    }

    @Test
    fun convert_to_YAML_and_back2() {
        checkYamlConversion(this.range2, Range, this.context2) shouldBe """
        reference: value
        from: test
        to: test999
        inclusiveFrom: true
        inclusiveTo: false

        """.trimIndent()
    }
}
