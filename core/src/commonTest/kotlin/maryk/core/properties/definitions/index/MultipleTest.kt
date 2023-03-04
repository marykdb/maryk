package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MultipleTest {
    private val multiple = TestMarykModel.run {
        Multiple(
            UUIDKey,
            Reversed(bool.ref()),
            multi.typeRef(),
            string.ref(),
            Reversed(string.ref()),
            int.ref()
        )
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = TestMarykModel
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = multiple,
            dataModel = Multiple.Model.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = multiple,
            dataModel = Multiple.Model.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect(
            """
            - !UUID
            - !Reversed bool
            - !Ref multi.*
            - !Ref string
            - !Reversed string
            - !Ref int

            """.trimIndent()
        ) {
            checkYamlConversion(
                value = multiple,
                dataModel = Multiple.Model.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("040101020b31020a69020a09020b09020a11") { multiple.toReferenceStorageByteArray().toHex() }
    }
}
