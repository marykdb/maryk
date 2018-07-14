package maryk.core.query.changes

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class DataObjectVersionedChangeTest {
    private val key1 = TestMarykModel.key(
        byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
    )

    private val subModel = TestMarykModel.ref { embeddedValues }

    private val dataObjectVersionedChanges = DataObjectVersionedChange(
        key = key1,
        changes = listOf(
            VersionedChanges(
                219674127L.toUInt64(),
                listOf(
                    ObjectSoftDeleteChange(true),
                    ListChange(TestMarykModel.ref { list }.change()),
                    SetChange(TestMarykModel.ref { set }.change()),
                    MapChange(TestMarykModel.ref { map }.change())
                )
            ),
            VersionedChanges(
                319674127L.toUInt64(),
                listOf(
                    Change(EmbeddedMarykModel.ref(subModel) { value } with "new"),
                    Delete(EmbeddedMarykModel.ref(subModel) { value }),
                    Check(EmbeddedMarykModel.ref(subModel) { value } with "current")
                )
            )
        )
    )

    private val context = DataModelPropertyContext(
        dataModels = mapOf(
            EmbeddedMarykModel.name to { EmbeddedMarykModel },
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, { this.context }) shouldBe """
        key: AAACKwEBAQAC
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
