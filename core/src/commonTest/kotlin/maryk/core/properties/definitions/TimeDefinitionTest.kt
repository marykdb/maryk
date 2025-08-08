package maryk.core.properties.definitions

import kotlinx.datetime.LocalTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.TimePrecision
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class TimeDefinitionTest {
    private val timesToTestNanos = arrayOf(
        LocalTime(12, 3, 5, 50_500_505),
        TimeDefinition.nowUTC(),
        TimeDefinition.MAX_IN_SECONDS,
        TimeDefinition.MAX_IN_MILLIS,
        TimeDefinition.MAX_IN_NANOS,
        TimeDefinition.MIN,
    )

    private val timesToTestMillis = arrayOf(
        LocalTime(12, 3, 5, 50_000_000),
        LocalTime.fromMillisecondOfDay(TimeDefinition.nowUTC().toMillisecondOfDay()),
        TimeDefinition.MAX_IN_SECONDS,
        TimeDefinition.MAX_IN_MILLIS,
        TimeDefinition.MIN
    )

    private val timesToTestSeconds = arrayOf(TimeDefinition.MAX_IN_SECONDS, TimeDefinition.MIN, LocalTime(13, 55, 44))

    private val def = TimeDefinition()

    private val defNano = TimeDefinition(
        precision = TimePrecision.NANOS
    )

    private val defMilli = TimeDefinition(
        precision = TimePrecision.MILLIS
    )

    private val defMaxDefined = TimeDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = TimeDefinition.MIN,
        maxValue = TimeDefinition.MAX_IN_MILLIS,
        precision = TimePrecision.MILLIS,
        default = LocalTime(12, 13, 14)
    )

    @OptIn(ExperimentalTime::class)
    @Test
    fun createNowTime() {
        val expected = Clock.System.now().toEpochMilliseconds() % (24 * 60 * 60 * 1000) / 1000
        val now = TimeDefinition.nowUTC().toSecondOfDay()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -1..1
        }
    }

    @Test
    fun convertNanosecondPrecisionValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (time in timesToTestNanos) {
            bc.reserve(
                defNano.calculateStorageByteLength(time)
            )
            defNano.writeStorageBytes(time, bc::write)
            expect(time) { defNano.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertMillisecondPrecisionValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (time in arrayOf(TimeDefinition.MAX_IN_MILLIS, TimeDefinition.MIN)) {
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
    fun convertNanosPrecisionValuesToTransportBytesAndBack() {
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

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, TimeDefinition.Model)
        }
    }

    @Test
    fun readNativeTimesToTime() {
        expect(LocalTime(3, 25, 45)) { this.def.fromNativeType(12345L) }
        expect(LocalTime(3, 25, 46)) { this.def.fromNativeType(12346) }
    }
}
