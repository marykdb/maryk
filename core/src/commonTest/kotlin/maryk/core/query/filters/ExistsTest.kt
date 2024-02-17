package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.expect

class ExistsTest {
    private val exists = Exists(
        TestMarykModel { string::ref }
    )
    private val existsMultiple = Exists(
        TestMarykModel { string::ref },
        TestMarykModel { int::ref },
        TestMarykModel { dateTime::ref }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun singleReference() {
        assertNotNull(
            existsMultiple.singleReference { it == TestMarykModel { int::ref } }
        )

        assertNull(
            existsMultiple.singleReference { it == TestMarykModel { uint::ref } }
        )

        assertNotNull(
            Exists(
                TestMarykModel { embeddedValues { model::ref } }
            ).singleReference { it == TestMarykModel { embeddedValues::ref } }
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.exists, Exists, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.exists, Exists, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string
            """.trimIndent()
        ) {
            checkYamlConversion(this.exists, Exists, { this.context })
        }

        expect(
            """
            - string
            - int
            - dateTime

            """.trimIndent()
        ) {
            checkYamlConversion(this.existsMultiple, Exists, { this.context })
        }
    }
}
