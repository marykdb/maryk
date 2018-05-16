package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.WriteCacheFailer
import maryk.lib.exceptions.ParseException
import maryk.lib.time.Date
import maryk.lib.time.Instant
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class DateDefinitionTest {
    private val datesToTest = arrayOf(
        Date.nowUTC(),
        Date.MAX,
        Date.MIN
    )

    private val def = DateDefinition()
    private val defMaxDefined = DateDefinition(
        indexed = true,
        required = false,
        final = true,
        searchable = false,
        unique = true,
        fillWithNow = true,
        maxValue = Date.MAX,
        minValue = Date.MIN,
        default = Date(1970, 12, 1)
    )

    @Test
    fun create_now_date() {
        val currentEpochDay = Instant.getCurrentEpochTimeInMillis() / (24 * 60 * 60 * 1000)
        def.createNow().epochDay shouldBe currentEpochDay
    }

    @Test
    fun convert_values_to_storage_bytes_and_back() {
        val bc = ByteCollector()
        for (it in datesToTest) {
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
        val cacheFailer = WriteCacheFailer()

        for (it in datesToTest) {
            bc.reserve(
                def.calculateTransportByteLength(it, cacheFailer)
            )
            def.writeTransportBytes(it, cacheFailer, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_values_to_String_and_back() {
        for (it in datesToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalid_String_value_should_throw_exception() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, DateDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, DateDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, DateDefinition.Model)
        checkJsonConversion(this.defMaxDefined, DateDefinition.Model)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, DateDefinition.Model)
        checkYamlConversion(this.defMaxDefined, DateDefinition.Model) shouldBe """
        indexed: true
        searchable: false
        required: false
        final: true
        unique: true
        minValue: -99999999-01-01
        maxValue: 99999999-12-31
        default: 1970-12-01
        fillWithNow: true

        """.trimIndent()
    }
}
