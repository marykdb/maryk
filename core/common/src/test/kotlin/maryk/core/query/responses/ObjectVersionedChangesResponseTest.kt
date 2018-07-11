package maryk.core.query.responses

import maryk.EmbeddedMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
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
import maryk.test.shouldBe
import kotlin.test.Test

class ObjectVersionedChangesResponseTest {
    private val key = TestMarykObject.key("AAACKwEBAQAC")

    private val subModel = TestMarykObject.ref { embeddedObject }

    private val objectVersionedChangesResponse = ObjectVersionedChangesResponse(
        TestMarykObject,
        listOf(
            DataObjectVersionedChange(
                key = key,
                changes = listOf(
                    VersionedChanges(
                        219674127L.toUInt64(),
                        listOf(
                            ObjectSoftDeleteChange(true),
                            ListChange(TestMarykObject.ref { list }.change()),
                            SetChange(TestMarykObject.ref { set }.change()),
                            MapChange(TestMarykObject.ref { map }.change())
                        )
                    ),
                    VersionedChanges(
                        319674127L.toUInt64(),
                        listOf(
                            Change(EmbeddedMarykObject.ref(subModel) { value } with "new"),
                            Delete(EmbeddedMarykObject.ref(subModel) { value }),
                            Check(EmbeddedMarykObject.ref(subModel) { value } with "current")
                        )
                    )
                )
            )
        )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        dataModels = mapOf(
            EmbeddedMarykObject.name to { EmbeddedMarykObject },
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.objectVersionedChangesResponse, ObjectVersionedChangesResponse, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.objectVersionedChangesResponse, ObjectVersionedChangesResponse, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.objectVersionedChangesResponse, ObjectVersionedChangesResponse, { this.context }) shouldBe """
        dataModel: TestMarykObject
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
              embeddedObject.value: new
            - !Delete embeddedObject.value
            - !Check
              embeddedObject.value: current

        """.trimIndent()
    }
}
