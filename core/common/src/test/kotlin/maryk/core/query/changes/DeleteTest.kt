package maryk.core.query.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class DeleteTest {
    private val propertyDelete = Delete(
        TestMarykObject.ref { string }
    )

    private val propertyDeleteMultiple = Delete(
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
        checkProtoBufConversion(this.propertyDelete, Delete, { this.context })
        checkProtoBufConversion(this.propertyDeleteMultiple, Delete, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.propertyDelete, Delete, { this.context })
        checkJsonConversion(this.propertyDeleteMultiple, Delete, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.propertyDelete, Delete, { this.context }) shouldBe """
        string
        """.trimIndent()

        checkYamlConversion(this.propertyDeleteMultiple, Delete, { this.context }) shouldBe """
        - string
        - int
        - dateTime

        """.trimIndent()
    }
}
