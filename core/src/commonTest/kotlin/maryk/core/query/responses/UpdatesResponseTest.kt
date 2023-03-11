package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.testMarykModelObject
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class UpdatesResponseTest {
    private val key = TestMarykModel.key("AAACKwEAAg")

    private val updatesResponse = UpdatesResponse(
        TestMarykModel,
        listOf(
            OrderedKeysUpdate(listOf(key), 1234uL),
            AdditionUpdate(key, 1234uL, 1233uL, 0, false, testMarykModelObject),
            ChangeUpdate(key, 1235uL, 4, listOf(
                Change(
                    TestMarykModel { string::ref } with "ha 2"
                )
            )),
            RemovalUpdate(key, 1236uL, SoftDelete)
        )
    )

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(updatesResponse, UpdatesResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(updatesResponse, UpdatesResponse.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            dataModel: TestMarykModel
            updates:
            - !OrderedKeys
              keys: [AAACKwEAAg]
              version: 1234
            - !Addition
              key: AAACKwEAAg
              version: 1234
              firstVersion: 1233
              insertionIndex: 0
              isDeleted: false
              values:
                string: haas
                int: 4
                uint: 53
                double: 3.5555
                dateTime: '2017-12-05T12:40'
                bool: true
                enum: V1(1)
            - !Change
              key: AAACKwEAAg
              version: 1235
              index: 4
              changes:
              - !Change
                string: ha 2
            - !Removal
              key: AAACKwEAAg
              version: 1236
              reason: SoftDelete

            """.trimIndent()
        ) {
            checkYamlConversion(updatesResponse, UpdatesResponse.Model, { this.context })
        }
    }
}
