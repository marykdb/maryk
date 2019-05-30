package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.TimePrecision
import maryk.lib.exceptions.ParseException
import maryk.lib.time.Instant
import maryk.lib.time.Time
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

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

    private val defMaxDefined = TimeDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = Time.MIN,
        maxValue = Time.MAX_IN_MILLIS,
        fillWithNow = true,
        precision = TimePrecision.MILLIS,
        default = Time(12, 13, 14)
    )

    @Test
    fun createNowTime() {
        val expected = Instant.getCurrentEpochTimeInMillis() % (24 * 60 * 60 * 1000) / 1000
        val now = def.createNow().toSecondsOfDay()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -1..1
        }
    }

    @Test
    fun convertMillisecondPrecisionValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (time in arrayOf(Time.MAX_IN_MILLIS, Time.MIN)) {
            bc.reserve(
                defMilli.calculateStorageByteLength(time)
            )
            defMilli.writeStorageBytes(time, bc::write)
            expect(time) { defMilli.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertSecondsPrecisionValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (time in timesToTestSeconds) {
            bc.reserve(
                def.calculateStorageByteLength(time)
            )
            def.writeStorageBytes(time, bc::write)
            expect(time) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertSecondsPrecisionValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (time in timesToTestSeconds) {
            bc.reserve(def.calculateTransportByteLength(time, cacheFailer))
            def.writeTransportBytes(time, cacheFailer, bc::write)
            expect(time) { def.readTransportBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertMillisPrecisionValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (time in timesToTestMillis) {
            bc.reserve(defMilli.calculateTransportByteLength(time, cacheFailer))
            defMilli.writeTransportBytes(time, cacheFailer, bc::write)
            expect(time) { defMilli.readTransportBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (time in timesToTestMillis) {
            val b = def.asString(time)
            expect(time) { def.fromString(b) }
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
        checkProtoBufConversion(this.def, TimeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, TimeDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, TimeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, TimeDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, TimeDefinition.Model)

        expect(
            """
            required: false
            final: true
            unique: true
            precision: MILLIS
            minValue: '00:00'
            maxValue: '23:59:59.999'
            default: '12:13:14'
            fillWithNow: true

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, TimeDefinition.Model)
        }
    }

    @Test
    fun readNativeTimesToTime() {
        expect(Time(3, 25, 45)) { this.def.fromNativeType(12345L) }
        expect(Time(3, 25, 46)) { this.def.fromNativeType(12346) }
    }
}
