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
        lastVersion = 12345uL
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.dataObjectChange, DataObjectChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
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
