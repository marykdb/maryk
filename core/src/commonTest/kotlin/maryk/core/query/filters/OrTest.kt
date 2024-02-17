package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class OrTest {
    private val or = Or(
        Exists(SimpleMarykModel { value::ref }),
        Equals(SimpleMarykModel { value::ref } with "hoi")
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.or, Or, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.or, Or, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            - !Exists value
            - !Equals
              value: hoi

            """.trimIndent()
        ) {
            checkYamlConversion(this.or, Or, { this.context })
        }
    }
}
