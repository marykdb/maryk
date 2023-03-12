package maryk.core.properties.definitions

import kotlinx.datetime.LocalDate
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class DateDefinitionTest {
    private val datesToTest = arrayOf(
        DateDefinition.nowUTC(),
        DateDefinition.MAX,
        DateDefinition.MIN
    )

    private val def = DateDefinition()
    private val defMaxDefined = DateDefinition(
        required = false,
        final = true,
        unique = true,
        maxValue = DateDefinition.MAX,
        minValue = DateDefinition.MIN,
        default = LocalDate(1970, 12, 1)
    )

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (date in datesToTest) {
            bc.reserve(
                def.calculateStorageByteLength(date)
            )
            def.writeStorageBytes(date, bc::write)
            expect(date) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (date in datesToTest) {
            bc.reserve(
                def.calculateTransportByteLength(date, cacheFailer)
            )
            def.writeTransportBytes(date, cacheFailer, bc::write)
            expect(date) { def.readTransportBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (date in datesToTest) {
            val b = def.asString(date)
            expect(date) { def.fromString(b) }
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        assertFailsWith<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, DateDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, DateDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, DateDefinition.Model)
        checkJsonConversion(this.defMaxDefined, DateDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, DateDefinition.Model)

        expect(
            """
            required: false
            final: true
            unique: true
            minValue: -999999-01-01
            maxValue: +999999-12-31
            default: 1970-12-01

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, DateDefinition.Model)
        }
    }
}
