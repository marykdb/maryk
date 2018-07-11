package maryk.core.query.changes

import maryk.EmbeddedMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class VersionedChangesTest {
    private val subModelValue = TestMarykObject { embeddedObject ref { value } }

    private val versionedChanges = VersionedChanges(
        219674127L.toUInt64(),
        listOf(
            Change(subModelValue with "new"),
            Delete(subModelValue),
            Check(subModelValue with "current"),
            ObjectSoftDeleteChange(true),
            ListChange(TestMarykObject.ref { list }.change()),
            SetChange(TestMarykObject.ref { set }.change()),
            MapChange(TestMarykObject.ref { map }.change())
        )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        dataModels = mapOf(
            EmbeddedMarykObject.name to { EmbeddedMarykObject },
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, ObjectPropertyDefinitions<Any>>
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
          embeddedObject.value: new
        - !Delete embeddedObject.value
        - !Check
          embeddedObject.value: current
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
