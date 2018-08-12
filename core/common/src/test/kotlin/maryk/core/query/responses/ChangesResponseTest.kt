package maryk.core.query.responses

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class ChangesResponseTest {
    private val key = SimpleMarykModel.key("+1xO4zD4R5sIMcS9pXTZEA")

    private val objectChangesResponse = ChangesResponse(
        SimpleMarykModel,
        listOf(
            key.change(
                Change(SimpleMarykModel.ref { value } with "hoho"),
                Delete(SimpleMarykModel.ref { value }),
                lastVersion = 14141L.toUInt64()
            )
        )
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.objectChangesResponse, ChangesResponse, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.objectChangesResponse, ChangesResponse, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.objectChangesResponse, ChangesResponse, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        changes:
        - key: +1xO4zD4R5sIMcS9pXTZEA
          changes:
          - !Change
            value: hoho
          - !Delete value
          lastVersion: 14141

        """.trimIndent()
    }
}
