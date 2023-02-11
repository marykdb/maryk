package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.expect

class EqualsTest {
    private val equals = Equals(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun singleReference() {
        assertNotNull(
            equals.singleReference { it == TestMarykModel { int::ref } }
        )

        assertNull(
            equals.singleReference { it == TestMarykModel { uint::ref } }
        )

        assertNotNull(
            Equals(
                TestMarykModel { embeddedValues { value::ref } } with "test"
            ).singleReference { it == TestMarykModel { embeddedValues::ref } }
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.equals, Equals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "string": "test",
              "int": 5
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.equals, Equals, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 5

            """.trimIndent()
        ) {
            checkYamlConversion(this.equals, Equals, { this.context })
        }
    }
}
