package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Date
import maryk.core.time.Instant
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.fail

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
            minValue = Date.MIN
    )

    @Test
    fun `create now date`() {
        val currentEpochDay = Instant.getCurrentEpochTimeInMillis() / (24 * 60 * 60 * 1000)
        def.createNow().epochDay shouldBe currentEpochDay
    }

    @Test
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        datesToTest.forEach {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollector()
        datesToTest.forEach {
            bc.reserve(
                    def.calculateTransportByteLength(it, { fail("Should not call") })
            )
            def.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to String and back`() {
        datesToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun `invalid String value should throw exception`() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, DateDefinition)
        checkProtoBufConversion(this.defMaxDefined, DateDefinition)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, DateDefinition)
        checkJsonConversion(this.defMaxDefined, DateDefinition)
    }
}