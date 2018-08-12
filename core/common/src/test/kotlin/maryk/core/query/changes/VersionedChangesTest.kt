package maryk.core.query.changes

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class VersionedChangesTest {
    private val subModelValue = TestMarykModel { embeddedValues ref { value } }

    private val versionedChanges = VersionedChanges(
        219674127L.toUInt64(),
        listOf(
            Change(subModelValue with "new"),
            Delete(subModelValue),
            Check(subModelValue with "current"),
            ObjectSoftDeleteChange(true),
            ListChange(TestMarykModel.ref { list }.change()),
            SetChange(TestMarykModel.ref { set }.change()),
            MapChange(TestMarykModel.ref { map }.change())
        )
    )

    private val context = RequestContext(
        dataModels = mapOf(
            EmbeddedMarykModel.name to { EmbeddedMarykModel },
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
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
        - !MapChange
          map:

        """.trimIndent()
    }
}
