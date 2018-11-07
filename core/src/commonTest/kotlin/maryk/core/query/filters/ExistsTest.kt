package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

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
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.exists, Exists, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.exists, Exists, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.exists, Exists, { this.context }) shouldBe """
        string
        """.trimIndent()

        checkYamlConversion(this.existsMultiple, Exists, { this.context }) shouldBe """
        - string
        - int
        - dateTime

        """.trimIndent()
    }
}
