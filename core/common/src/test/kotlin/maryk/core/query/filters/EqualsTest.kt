package maryk.core.query.filters

import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class EqualsTest {
    private val equals = Equals(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.equals, Equals, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.equals, Equals, { this.context }) shouldBe """
        {
        	"string": "test",
        	"int": 5
        }
        """.trimIndent()
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.equals, Equals, { this.context }) shouldBe """
        string: test
        int: 5

        """.trimIndent()
    }
}
