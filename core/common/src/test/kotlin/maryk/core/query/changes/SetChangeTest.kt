package maryk.core.query.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.lib.time.Date
import maryk.test.shouldBe
import kotlin.test.Test

class SetChangeTest {
    private val setPropertyChange = SetChange(
        reference = TestMarykObject.ref { set },
        addValues = setOf(
            Date(2014, 4, 14),
            Date(2013, 3, 13)
        ),
        deleteValues = setOf(Date(2018, 7, 17))
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.setPropertyChange, SetChange, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.setPropertyChange, SetChange, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.setPropertyChange, SetChange, this.context) shouldBe """
        reference: set
        addValues: [2014-04-14, 2013-03-13]
        deleteValues: [2018-07-17]
        
        """.trimIndent()
    }
}
