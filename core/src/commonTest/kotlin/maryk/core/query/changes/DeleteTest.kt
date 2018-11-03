package maryk.core.query.changes

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.shouldBe
import kotlin.test.Test

class DeleteTest {
    private val propertyDelete = Delete(
        TestMarykModel.ref { string }
    )

    private val propertyDeleteMultiple = Delete(
        TestMarykModel.ref { string },
        TestMarykModel.ref { int },
        TestMarykModel.ref { dateTime }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
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
