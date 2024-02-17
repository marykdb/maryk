package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class VersionedChangesTest {
    private val subModelValue = TestMarykModel { embeddedValues { value::ref } }

    private val versionedChanges = VersionedChanges(
        219674127uL,
        listOf(
            Change(subModelValue with "new"),
            Delete(subModelValue),
            Check(subModelValue with "current"),
            ObjectSoftDeleteChange(true),
            ListChange(TestMarykModel { list::ref }.change()),
            SetChange(TestMarykModel { set::ref }.change()),
            ObjectCreate
        )
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
        checkProtoBufConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            version: 219674127
            changes:
            - !Change
              embeddedValues.value: new
            - !Delete embeddedValues.value
            - !Check
              embeddedValues.value: current
            - !ObjectDelete
              isDeleted: true
            - !ListChange
              list:
            - !SetChange
              set:
            - !ObjectCreate

            """.trimIndent()
        ) {
            checkYamlConversion(this.versionedChanges, VersionedChanges, { this.context })
        }
    }
}
