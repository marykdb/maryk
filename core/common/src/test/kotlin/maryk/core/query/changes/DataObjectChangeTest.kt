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

class DataObjectChangeTest {
    private val key1 = TestMarykModel.key(
        byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
    )

    private val subModel = TestMarykModel.ref { embeddedValues }

    private val dataObjectChange = key1.change(
        Change(EmbeddedMarykModel.ref(subModel) { value } with  "new"),
        Delete(EmbeddedMarykModel.ref(subModel) { value }),
        Check(EmbeddedMarykModel.ref(subModel) { value } with "current"),
        ObjectSoftDeleteChange(true),
        ListChange(
            TestMarykModel.ref { list }.change(
                addValuesToEnd = listOf(1,2,3)
            )
        ),
        SetChange(TestMarykModel.ref { set }.change()),
        MapChange(TestMarykModel.ref { map }.change()),
        lastVersion = 12345L.toUInt64()
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name to { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.dataObjectChange, DataObjectChange, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.dataObjectChange, DataObjectChange, { this.context }) shouldBe """
        key: AAACKwEBAQAC
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
            addValuesToEnd: [1, 2, 3]
        - !SetChange
          set:
        - !MapChange
          map:
        lastVersion: 12345

        """.trimIndent()
    }
}
