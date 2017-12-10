package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import maryk.core.time.Instant
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

internal class DateTimeDefinitionTest {
    private val dateTimesToTest = arrayOf(
            DateTime.nowUTC(),
            DateTime.MAX_IN_SECONDS,
            DateTime.MAX_IN_MILLIS,
            DateTime.MIN
    )

    private val def = DateTimeDefinition()

    private val defMilli = DateTimeDefinition(
            precision = TimePrecision.MILLIS
    )

    private val defMaxDefined = DateTimeDefinition(
            indexed = true,
            required = false,
            final = true,
            searchable = false,
            unique = true,
            fillWithNow = true,
            precision = TimePrecision.MILLIS,
            minValue = DateTime.MIN,
            maxValue = DateTime.MAX_IN_MILLIS
    )

    @Test
    fun `create now date time`() {
        val now = def.createNow().toEpochMilli()
        val expected = Instant.getCurrentEpochTimeInMillis()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -100..100
        }
    }

    @Test
    fun `convert values with milliseconds precision to storage bytes and back`() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            bc.reserve(
                    defMilli.calculateStorageByteLength(it)
            )
            defMilli.writeStorageBytes(it, bc::write)
            defMilli.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values with seconds precision to storage bytes and back`() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values with seconds precision to transport bytes and back`() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MIN, DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            bc.reserve(defMilli.calculateTransportByteLength(it, { fail("Should not call") }))
            defMilli.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            defMilli.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values with millisecond precision to transport bytes and back`() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            bc.reserve(def.calculateTransportByteLength(it, { fail("Should not call") }))
            def.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to String and back`() {
        dateTimesToTest.forEach {
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
        checkProtoBufConversion(this.def, DateTimeDefinition)
        checkProtoBufConversion(this.defMaxDefined, DateTimeDefinition)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, DateTimeDefinition)
        checkJsonConversion(this.defMaxDefined, DateTimeDefinition)
    }
}
