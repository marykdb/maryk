package maryk.core.properties.definitions

import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Time
import maryk.core.properties.types.TimePrecision
import maryk.core.time.Instant
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

internal class TimeDefinitionTest {
    private val timesToTestMillis = arrayOf(
            Time(12, 3, 5, 50),
            Time.nowUTC(),
            Time.MAX_IN_SECONDS,
            Time.MAX_IN_MILLIS,
            Time.MIN
    )

    private val timesToTestSeconds = arrayOf(Time.MAX_IN_SECONDS, Time.MIN, Time(13, 55, 44))

    private val def = TimeDefinition()

    private val defMilli = TimeDefinition(
            precision = TimePrecision.MILLIS
    )

    @Test
    fun createNow() {
        val expected = Instant.getCurrentEpochTimeInMillis()% (24 * 60 * 60 * 1000) / 1000
        val now = def.createNow().toSecondsOfDay()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -1..1
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
