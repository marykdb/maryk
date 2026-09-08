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
        TestMarykModel.ref { string }
    )
    private val existsMultiple = Exists(
        TestMarykModel.ref { string },
        TestMarykModel.ref { int },
        TestMarykModel.ref { dateTime }
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
            existsMultiple.singleReference { it == TestMarykModel.ref { int } }
        )

        assertNull(
            existsMultiple.singleReference { it == TestMarykModel.ref { uint } }
        )

        assertNotNull(
            Exists(
                TestMarykModel.ref { embeddedValues { model } }
            ).singleReference { it == TestMarykModel.ref { embeddedValues } }
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
