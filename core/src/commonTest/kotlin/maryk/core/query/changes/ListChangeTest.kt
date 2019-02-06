package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ListChangeTest {
    private val listPropertyChange = ListChange(
        TestMarykModel.ref { listOfString }.change(
            addValuesAtIndex = mapOf(2u to "a", 3u to "abc"),
            addValuesToEnd = listOf("four", "five"),
            deleteValues = listOf("three")
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.listPropertyChange, ListChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.listPropertyChange, ListChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.listPropertyChange, ListChange, { this.context }) shouldBe """
        listOfString:
          addValuesToEnd: [four, five]
          addValuesAtIndex:
            2: a
            3: abc
          deleteValues: [three]

        """.trimIndent()
    }
}
