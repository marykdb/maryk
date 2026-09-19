package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
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

    private val subModel = TestMarykModel.ref { embeddedValues }

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
                            ListChange(TestMarykModel.ref { list }.change()),
                            SetChange(TestMarykModel.ref { set }.change()),
                            MultiTypeChange(TestMarykModel.ref { multi } withType S3)
                        )
                    ),
                    VersionedChanges(
                        319674127uL,
                        listOf(
                            ObjectCreate,
                            Change(EmbeddedMarykModel.ref(subModel) { value } with "new"),
                            Change(EmbeddedMarykModel.ref(subModel) { value }  with null),
                            Check(EmbeddedMarykModel.ref(subModel) { value } with "current")
                        )
                    )
                )
            )
        ),
        dataFetchType = FetchByKey,
    )

    private val context = RequestContext(
        dataModels = mapOf(
            EmbeddedMarykModel.Meta.name to DataModelReference(EmbeddedMarykModel),
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
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
                  list: {}
                - !SetChange
                  set: {}
                - !TypeChange
                  multi: S3
              - version: 319674127
                changes:
                - !ObjectCreate
                - !Change
                  embeddedValues.value: new
                - !Change
                  embeddedValues.value: null
                - !Check
                  embeddedValues.value: current
            dataFetchType: !Key

            """.trimIndent()
        ) {
            checkYamlConversion(this.objectChangesResponse, ChangesResponse, { this.context })
        }
    }
}
