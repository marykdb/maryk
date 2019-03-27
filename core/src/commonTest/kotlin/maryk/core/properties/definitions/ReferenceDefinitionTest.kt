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
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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
        def.dataModel shouldBe TestMarykModel
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (it in refToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        shouldThrow<ParseException> {
            def.fromString("wrong§")
        }
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (it in refToTest) {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
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
        checkProtoBufConversion(this.def, ReferenceDefinition.Model, { DefinitionsContext() })
        checkProtoBufConversion(this.defMaxDefined, ReferenceDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, ReferenceDefinition.Model, { DefinitionsContext() })
        checkJsonConversion(this.defMaxDefined, ReferenceDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, ReferenceDefinition.Model, { DefinitionsContext() })
        checkYamlConversion(this.defMaxDefined, ReferenceDefinition.Model, { DefinitionsContext() }) shouldBe """
        required: false
        final: true
        unique: true
        minValue: AAAAAAAAAA
        maxValue: /////////w
        default: AQEBAQEBAQ
        dataModel: TestMarykModel

        """.trimIndent()
    }
}
