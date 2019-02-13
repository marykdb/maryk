package maryk.core.properties.definitions.key

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.TestMarykModel.Properties
import maryk.test.models.TestMarykModel.Properties.bool
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.models.TestMarykModel.Properties.multi
import maryk.test.shouldBe
import kotlin.test.Test

class MultipleTest {
    private val multiple = Multiple(
        UUIDKey,
        Reversed(bool.ref()),
        TypeId(multi.ref()),
        int.ref()
    )

    private val context = DefinitionsConversionContext(
        propertyDefinitions = Properties
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        ) shouldBe """
        - !UUID
        - !Reversed bool
        - !TypeId multi
        - !Ref int

        """.trimIndent()
    }
}
