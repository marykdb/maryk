package maryk.core.properties.definitions

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.properties.ByteCollector
import maryk.core.properties.types.Key
import maryk.core.query.DataModelContext
import maryk.lib.exceptions.ParseException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class ReferenceDefinitionTest {
    private val refToTest = arrayOf<Key<TestMarykObject>>(
        Key(ByteArray(9, { ZERO_BYTE })),
        Key(ByteArray(9, { MAX_BYTE })),
        Key(ByteArray(9, { if (it % 2 == 1) 0b1000_1000.toByte() else MAX_BYTE }))
    )

    val def = ReferenceDefinition(
        dataModel = { TestMarykObject }
    )
    val defMaxDefined = ReferenceDefinition(
        indexed = true,
        required = false,
        final = true,
        searchable = false,
        unique = true,
        minValue = refToTest[0],
        maxValue = refToTest[1],
        dataModel = { TestMarykObject },
        default = Key(ByteArray(9, { 1 }))
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe TestMarykObject
    }

    @Test
    fun convert_values_to_String_and_back() {
        for (it in refToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }
    @Test
    fun invalid_String_value_should_throw_exception() {
        shouldThrow<ParseException> {
            def.fromString("wrong§")
        }
    }

    @Test
    fun convert_values_to_storage_bytes_and_back() {
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
    fun convert_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        for (it in refToTest) {
            checkProtoBufConversion(bc, it, this.def)
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, ReferenceDefinition.Model,{ DataModelContext() })
        checkProtoBufConversion(this.defMaxDefined, ReferenceDefinition.Model, { DataModelContext() })
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, ReferenceDefinition.Model, { DataModelContext() })
        checkJsonConversion(this.defMaxDefined, ReferenceDefinition.Model, { DataModelContext() })
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, ReferenceDefinition.Model, { DataModelContext() })
        checkYamlConversion(this.defMaxDefined, ReferenceDefinition.Model, { DataModelContext() }) shouldBe """
        indexed: true
        searchable: false
        required: false
        final: true
        unique: true
        minValue: AAAAAAAAAAAA
        maxValue: ////////////
        default: AQEBAQEBAQEB
        dataModel: TestMarykObject

        """.trimIndent()
    }
}
