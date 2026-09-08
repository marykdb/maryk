package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class GreaterThanEqualsTest {
    private val greaterThanEquals = GreaterThanEquals(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 6
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 6

            """.trimIndent()
        ) {
            checkYamlConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
        }
    }
}
