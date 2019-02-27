package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import maryk.test.models.TestMarykModel.Properties
import maryk.test.models.TestMarykModel.Properties.bool
import maryk.test.models.TestMarykModel.Properties.int
import maryk.test.models.TestMarykModel.Properties.multi
import maryk.test.models.TestMarykModel.Properties.string
import maryk.test.shouldBe
import kotlin.test.Test

class MultipleTest {
    private val multiple = Multiple(
        UUIDKey,
        Reversed(bool.ref()),
        multi.typeRef(),
        string.ref(),
        Reversed(string.ref()),
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
        - !Ref multi.*
        - !Ref string
        - !Reversed string
        - !Ref int

        """.trimIndent()
    }

    @Test
    fun toReferenceStorageBytes() {
        multiple.toReferenceStorageByteArray().toHex() shouldBe "040101020b31020a69020a09020b09020a11"
    }
}
