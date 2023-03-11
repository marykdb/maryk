package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.expect

class AndTest {
    private val and = And(
        Exists(SimpleMarykModel { value::ref }),
        Equals(
            SimpleMarykModel { value::ref } with "hoi"
        )
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
        ),
        dataModel = SimpleMarykModel.Model
    )

    @Test
    fun singleReference() {
        assertNotNull(
            and.singleReference { it == SimpleMarykModel { value::ref } }
        )

        assertNull(
            and.singleReference { it == TestMarykModel { uint::ref } }
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.and, And, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.and, And.Model, { this.context })
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
            checkYamlConversion(this.and, And.Model, { this.context })
        }
    }
}
