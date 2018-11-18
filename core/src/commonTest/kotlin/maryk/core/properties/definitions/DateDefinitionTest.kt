package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.lib.exceptions.ParseException
import maryk.lib.time.Date
import maryk.lib.time.Instant
import maryk.test.ByteCollector
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
        unique = true,
        fillWithNow = true,
        maxValue = Date.MAX,
        minValue = Date.MIN,
        default = Date(1970, 12, 1)
    )

    @Test
    fun createNowDate() {
        val currentEpochDay = Instant.getCurrentEpochTimeInMillis() / (24 * 60 * 60 * 1000)
        def.createNow().epochDay shouldBe currentEpochDay.toInt()
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
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
    fun convertValuesToTransportBytesAndBack() {
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
    fun convertValuesToStringAndBack() {
        for (it in datesToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        shouldThrow<ParseException> {
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
        checkYamlConversion(this.defMaxDefined, DateDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        unique: true
        minValue: -999999-01-01
        maxValue: 999999-12-31
        default: 1970-12-01
        fillWithNow: true

        """.trimIndent()
    }
}
