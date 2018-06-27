package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class AndTest {
    private val and = And(
        Exists(SimpleMarykObject.ref{ value }),
        Equals(
            SimpleMarykObject.ref{ value } with "hoi"
        )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to { SimpleMarykObject }
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.and, And, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.and, And, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.and, And, { this.context }) shouldBe """
        - !Exists value
        - !Equals
          value: hoi

        """.trimIndent()
    }
}
