package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class DataObjectVersionedChangeTest {
    private val key1 = TestMarykObject.key(
        byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
    )

    private val subModel = TestMarykObject.ref { subModel }

    private val dataObjectVersionedChanges = DataObjectVersionedChange(
        key = key1,
        changes = listOf(
            VersionedChanges(
                219674127L.toUInt64(),
                listOf(
                    ObjectSoftDeleteChange(true),
                    ListChange(TestMarykObject.ref { list }),
                    SetChange(TestMarykObject.ref { set }),
                    MapChange(TestMarykObject.ref { map })
                )
            ),
            VersionedChanges(
                319674127L.toUInt64(),
                listOf(
                    Change(SubMarykObject.ref(subModel) { value }, "new"),
                    Delete(SubMarykObject.ref(subModel) { value }),
                    Check(SubMarykObject.ref(subModel) { value }, "current")
                )
            )
        )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        dataModels = mapOf(
            SubMarykObject.name to SubMarykObject,
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, this.context) shouldBe """
        |key: AAACKwEBAQAC
        |changes:
        |- version: 0x000000000d17f60f
        |  changes:
        |  - !ObjectDelete
        |    isDeleted: true
        |  - !ListChange
        |    reference: list
        |  - !SetChange
        |    reference: set
        |  - !MapChange
        |    reference: map
        |- version: 0x00000000130dd70f
        |  changes:
        |  - !Change
        |    reference: subModel.value
        |    value: new
        |  - !Delete
        |    reference: subModel.value
        |  - !Check
        |    reference: subModel.value
        |    value: current
        |""".trimMargin()
    }
}
