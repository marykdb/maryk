package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

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
            SetChange(TestMarykModel { set::ref }.change())
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
        checkProtoBufConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.versionedChanges, VersionedChanges, { this.context }) shouldBe """
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

        """.trimIndent()
    }
}
