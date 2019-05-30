package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ListChangeTest {
    private val listPropertyChange = ListChange(
        TestMarykModel { listOfString::ref }.change(
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
        expect(
            """
            listOfString:
              addValuesToEnd: [four, five]
              addValuesAtIndex:
                2: a
                3: abc
              deleteValues: [three]

            """.trimIndent()
        ) {
            checkYamlConversion(this.listPropertyChange, ListChange, { this.context })
        }
    }
}
