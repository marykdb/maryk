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
        checkProtoBufConversion(ObjectCreate, ObjectCreateModel, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(ObjectCreate, ObjectCreateModel, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """

            """.trimIndent()
        ) {
            checkYamlConversion(ObjectCreate, ObjectCreateModel, { this.context })
        }
    }
}
