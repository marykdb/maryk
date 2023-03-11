package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.RequestContext
import kotlin.test.Test
import kotlin.test.expect

class ObjectCreateTest {
    private val context = RequestContext(
        mapOf()
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(ObjectCreate, ObjectCreate.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(ObjectCreate, ObjectCreate.Model.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """

            """.trimIndent()
        ) {
            checkYamlConversion(ObjectCreate, ObjectCreate.Model.Model, { this.context })
        }
    }
}
