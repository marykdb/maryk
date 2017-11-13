package maryk.core.properties.definitions

import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import maryk.test.shouldBe
import maryk.test.shouldThrow
import java.time.LocalDateTime
import java.time.ZoneOffset
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

    private val def = DateTimeDefinition(
            name = "seconds"
    )

    private val defMilli = DateTimeDefinition(
            name = "milli",
            precision = TimePrecision.MILLIS
    )

    @Test
    fun createNow() {
        assertTrue {
            LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli() - def.createNow().toEpochMilli() in -20..20
        }
    }

    @Test
    fun convertStorageBytesMillis() {
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
    fun convertStorageBytesSeconds() {
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
    fun convertTransportBytesMillis() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MIN, DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            bc.reserve(defMilli.calculateTransportByteLength(it, { fail("Should not call") }))
            defMilli.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            defMilli.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertTransportBytesSeconds() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            bc.reserve(def.calculateTransportByteLength(it, { fail("Should not call") }))
            def.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertString() {
        dateTimesToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}
