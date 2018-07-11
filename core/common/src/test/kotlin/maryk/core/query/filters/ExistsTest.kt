package maryk.core.query.filters

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ExistsTest {
    private val exists = Exists(
        TestMarykObject.ref { string }
    )
    private val existsMultiple = Exists(
        TestMarykObject.ref { string },
        TestMarykObject.ref { int },
        TestMarykObject.ref { dateTime }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, ObjectPropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.exists, Exists, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.exists, Exists, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.exists, Exists, { this.context }) shouldBe """
        string
        """.trimIndent()

        checkYamlConversion(this.existsMultiple, Exists, { this.context }) shouldBe """
        - string
        - int
        - dateTime

        """.trimIndent()
    }
}
