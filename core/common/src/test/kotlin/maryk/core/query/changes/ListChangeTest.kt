package maryk.core.query.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ListChangeTest {
    private val listPropertyChange = ListChange(
        TestMarykObject.ref { listOfString }.change(
            addValuesAtIndex = mapOf(2 to "a", 3 to "abc"),
            addValuesToEnd = listOf("four", "five"),
            deleteAtIndex = setOf(0, 1),
            deleteValues = listOf("three")
        )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.listPropertyChange, ListChange, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.listPropertyChange, ListChange, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.listPropertyChange, ListChange, { this.context }) shouldBe """
        listOfString:
          addValuesToEnd: [four, five]
          addValuesAtIndex:
            2: a
            3: abc
          deleteValues: [three]
          deleteAtIndex: [0, 1]

        """.trimIndent()
    }
}
