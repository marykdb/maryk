package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class NotTest {
    private val not = Not(
        Exists(SimpleMarykModel { value::ref })
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.not, Not, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.not, Not, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            - !Exists value

            """.trimIndent()
        ) {
            checkYamlConversion(this.not, Not, { this.context })
        }
    }
}
