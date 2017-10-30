package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertTrue

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
            defMilli.readStorageBytes(null, bc.size, bc::read) shouldBe it
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
            def.readStorageBytes(null, bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertTransportBytesMillis() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MIN, DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            bc.reserve(defMilli.calculateTransportByteLength(it))
            defMilli.writeTransportBytes(it, bc::write)
            defMilli.readTransportBytes(null, bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertTransportBytesSeconds() {
        val bc = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            bc.reserve(def.calculateTransportByteLength(it))
            def.writeTransportBytes(it, bc::write)
            def.readTransportBytes(null, bc.size, bc::read) shouldBe it
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
