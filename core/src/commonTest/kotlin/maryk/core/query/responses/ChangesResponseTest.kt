@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.Delete
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MapChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ChangesResponseTest {
    private val key = TestMarykModel.key("AAACKwEBAQAC")

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
                            MapChange(TestMarykModel.ref { map }.change())
                        )
                    ),
                    VersionedChanges(
                        319674127uL,
                        listOf(
                            Change(EmbeddedMarykModel.ref(subModel) { value } with "new"),
                            Delete(EmbeddedMarykModel.ref(subModel) { value }),
                            Check(EmbeddedMarykModel.ref(subModel) { value } with "current")
                        )
                    )
                )
            )
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
        checkYamlConversion(this.objectChangesResponse, ChangesResponse, { this.context }) shouldBe """
        dataModel: TestMarykModel
        changes:
        - key: AAACKwEBAQAC
          changes:
          - version: 219674127
            changes:
            - !ObjectDelete
              isDeleted: true
            - !ListChange
              list:
            - !SetChange
              set:
            - !MapChange
              map:
          - version: 319674127
            changes:
            - !Change
              embeddedValues.value: new
            - !Delete embeddedValues.value
            - !Check
              embeddedValues.value: current

        """.trimIndent()
    }
}
