package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DeleteTest {
    private val propertyDelete = Delete(
        TestMarykModel { string::ref }
    )

    private val propertyDeleteMultiple = Delete(
        TestMarykModel { string::ref },
        TestMarykModel { int::ref },
        TestMarykModel { dateTime::ref }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.propertyDelete, Delete, { this.context })
        checkProtoBufConversion(this.propertyDeleteMultiple, Delete, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.propertyDelete, Delete, { this.context })
        checkJsonConversion(this.propertyDeleteMultiple, Delete, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string
            """.trimIndent()
        ) {
            checkYamlConversion(this.propertyDelete, Delete, { this.context })
        }

        expect(
            """
            - string
            - int
            - dateTime

            """.trimIndent()
        ) {
            checkYamlConversion(this.propertyDeleteMultiple, Delete, { this.context })
        }
    }
}
