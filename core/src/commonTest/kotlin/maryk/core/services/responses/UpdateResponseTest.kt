package maryk.core.services.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class UpdateResponseTest {
    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")

    private val updateResponse = UpdateResponse(
        id = 1234uL,
        dataModel = SimpleMarykModel,
        update = RemovalUpdate(
            key,
            version = 1234uL,
            reason = HardDelete
        )
    )

    private val context = RequestContext(
        DefinitionsContext(mutableMapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        )),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.updateResponse, UpdateResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "id": "1234",
              "dataModel": "SimpleMarykModel",
              "update": ["Removal", {
                "key": "0ruQCs38S2QaByYof+IJgA",
                "version": "1234",
                "reason": "HardDelete"
              }]
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.updateResponse, UpdateResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            id: 1234
            dataModel: SimpleMarykModel
            update: !Removal
              key: 0ruQCs38S2QaByYof+IJgA
              version: 1234
              reason: HardDelete

            """.trimIndent()
        ) {
            checkYamlConversion(this.updateResponse, UpdateResponse, { this.context })
        }
    }
}
