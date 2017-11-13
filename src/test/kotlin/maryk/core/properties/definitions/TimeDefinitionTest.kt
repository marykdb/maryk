package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Time
import maryk.core.properties.types.TimePrecision
import maryk.test.shouldThrow
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.Test

internal class TimeDefinitionTest {
    private val timesToTestMillis = arrayOf(
            Time(12, 3, 5, 50),
            Time.nowUTC(),
            Time.MAX_IN_SECONDS,
            Time.MAX_IN_MILLIS,
            Time.MIN
    )

    private val timesToTestSeconds = arrayOf(Time.MAX_IN_SECONDS, Time.MIN, Time(13, 55, 44))

    val def = TimeDefinition(
            name = "seconds"
    )

    private val defMilli = TimeDefinition(
            name = "milli",
            precision = TimePrecision.MILLIS
    )

    @Test
    fun createNow() {
        assertTrue {
            LocalTime.now(ZoneOffset.UTC).toSecondOfDay() - def.createNow().toSecondsOfDay() in 0..1
        }
    }

    @Test
    fun convertStorageBytesMillis() {
        val bc = ByteCollector()
        arrayOf(Time.MAX_IN_MILLIS, Time.MIN).forEach {
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
        timesToTestSeconds.forEach {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertTransportBytesSeconds() {
        val bc = ByteCollector()
        timesToTestSeconds.forEach {
            bc.reserve(def.calculateTransportByteLength(it, { fail("Should not call") }))
            def.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertTransportBytesMillis() {
        val bc = ByteCollector()
        timesToTestMillis.forEach {
            bc.reserve(defMilli.calculateTransportByteLength(it, { fail("Should not call") }))
            defMilli.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            defMilli.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertString() {
        timesToTestMillis.forEach {
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
