package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.Delete
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.pairs.withType
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ChangesResponseTest {
    private val key = TestMarykModel.key("AAACKwEAAg")

    private val subModel = TestMarykModel { embeddedValues::ref }

    private val objectChangesResponse = ChangesResponse(
        TestMarykModel,
        listOf(
            DataObjectVersionedChange(
                key = key,
                changes = listOf(
                    VersionedChanges(
                        219674127uL,
                        listOf(
                            ObjectSoftDeleteChange(true),
                            ListChange(TestMarykModel { list::ref }.change()),
                            SetChange(TestMarykModel { set::ref }.change()),
                            MultiTypeChange(TestMarykModel { multi::ref } withType S3)
                        )
                    ),
                    VersionedChanges(
                        319674127uL,
                        listOf(
                            Change(EmbeddedMarykModel(subModel) { value::ref } with "new"),
                            Delete(EmbeddedMarykModel(subModel) { value::ref }),
                            Check(EmbeddedMarykModel(subModel) { value::ref } with "current")
                        )
                    )
                )
            )
        ),
        AggregationsResponse(
            "total" to ValueCountResponse(TestMarykModel { string::ref }, 1uL)
        )
    )

    private val context = RequestContext(
        dataModels = mapOf(
            EmbeddedMarykModel.name toUnitLambda { EmbeddedMarykModel },
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.objectChangesResponse, ChangesResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.objectChangesResponse, ChangesResponse, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            dataModel: TestMarykModel
            changes:
            - key: AAACKwEAAg
              changes:
              - version: 219674127
                changes:
                - !ObjectDelete
                  isDeleted: true
                - !ListChange
                  list:
                - !SetChange
                  set:
                - !TypeChange
                  multi: S3
              - version: 319674127
                changes:
                - !Change
                  embeddedValues.value: new
                - !Delete embeddedValues.value
                - !Check
                  embeddedValues.value: current
            aggregations:
              total: !ValueCount
                of: string
                value: 1

            """.trimIndent()
        ) {
            checkYamlConversion(this.objectChangesResponse, ChangesResponse, { this.context })
        }
    }
}
