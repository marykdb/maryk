package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class PrefixTest {
    private val prefix = SimpleMarykObject.ref { value } isPrefixedBy "te"

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to SimpleMarykObject
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.prefix, Prefix, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.prefix, Prefix, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.prefix, Prefix, this.context) shouldBe """
        value: te

        """.trimIndent()
    }
}
