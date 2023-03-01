package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.properties.types.Key
import maryk.core.query.DefinitionsContext
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV1_1WrongKey
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class ReferenceDefinitionTest {
    private val refToTest = arrayOf<Key<TestMarykModel>>(
        Key(ByteArray(7) { ZERO_BYTE }),
        Key(ByteArray(7) { MAX_BYTE }),
        Key(ByteArray(7) { if (it % 2 == 1) 0b1000_1000.toByte() else MAX_BYTE })
    )

    val def = ReferenceDefinition(
        dataModel = { TestMarykModel }
    )
    val defMaxDefined = ReferenceDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = refToTest[0],
        maxValue = refToTest[1],
        dataModel = { TestMarykModel },
        default = Key(ByteArray(7) { 1 })
    )

    @Test
    fun hasValues() {
        expect(TestMarykModel) { def.dataModel }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (key in refToTest) {
            val b = def.asString(key)
            expect(key) { def.fromString(b) }
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        assertFailsWith<ParseException> {
            def.fromString("wrong§")
        }
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (key in refToTest) {
            bc.reserve(
                def.calculateStorageByteLength(key)
            )
            def.writeStorageBytes(key, bc::write)
            expect(key) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        for (it in refToTest) {
            checkProtoBufConversion(bc, it, this.def)
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, ReferenceDefinition.Model.Model, { DefinitionsContext() })
        checkProtoBufConversion(this.defMaxDefined, ReferenceDefinition.Model.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, ReferenceDefinition.Model.Model, { DefinitionsContext() })
        checkJsonConversion(this.defMaxDefined, ReferenceDefinition.Model.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, ReferenceDefinition.Model.Model, { DefinitionsContext() })

        expect(
            """
            required: false
            final: true
            unique: true
            minValue: AAAAAAAAAA
            maxValue: /////////w
            default: AQEBAQEBAQ
            dataModel: TestMarykModel

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, ReferenceDefinition.Model.Model, { DefinitionsContext() })
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            ReferenceDefinition(dataModel = { ModelV1 }).compatibleWith(
                ReferenceDefinition(dataModel = { ModelV1_1 }),
            )
        }

        assertFalse {
            ReferenceDefinition(dataModel = { ModelV1 }).compatibleWith(
                ReferenceDefinition(dataModel = { ModelV1_1WrongKey })
            )
        }

        assertFalse {
            ReferenceDefinition(dataModel = { ModelV1 }).compatibleWith(
                ReferenceDefinition(dataModel = { TestMarykModel })
            )
        }
    }
}
