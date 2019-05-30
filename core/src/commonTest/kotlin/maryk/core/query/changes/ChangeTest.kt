package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ChangeTest {
    private val valueChange = Change(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun testValueChange() {
        expect(TestMarykModel { string::ref }) {
            valueChange.referenceValuePairs[0].reference as ValueWithFlexBytesPropertyReference<*, *, *, *>
        }
        expect("test") { valueChange.referenceValuePairs[0].value }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 5

            """.trimIndent()
        ) {
            checkYamlConversion(this.valueChange, Change, { this.context })
        }
    }
}
