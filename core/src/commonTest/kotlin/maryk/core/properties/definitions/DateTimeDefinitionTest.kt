package maryk.core.properties.definitions

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.DateUnit
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.roundToDateUnit
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

internal class DateTimeDefinitionTest {
    private val dateTimesMillisToTest = arrayOf(
        DateTimeDefinition.nowUTC().roundToDateUnit(DateUnit.Millis),
        DateTimeDefinition.MAX_IN_SECONDS,
        DateTimeDefinition.MAX_IN_MILLIS,
        DateTimeDefinition.MIN
    )

    private val dateTimesNanosToTest = arrayOf(
        DateTimeDefinition.nowUTC(),
        DateTimeDefinition.MAX_IN_NANOS,
        DateTimeDefinition.MAX_IN_MILLIS,
        DateTimeDefinition.MAX_IN_SECONDS,
        DateTimeDefinition.MIN
    )

    private val dateTimesSecondsToTest = arrayOf(
        DateTimeDefinition.nowUTC().roundToDateUnit(DateUnit.Seconds),
        DateTimeDefinition.MAX_IN_SECONDS,
        DateTimeDefinition.MIN
    )

    private val def = DateTimeDefinition()

    private val defMilli = DateTimeDefinition(
        precision = TimePrecision.MILLIS
    )

    private val defNano = DateTimeDefinition(
        precision = TimePrecision.NANOS
    )

    private val defMaxDefined = DateTimeDefinition(
        required = false,
        final = true,
        unique = true,
        precision = TimePrecision.MILLIS,
        minValue = DateTimeDefinition.MIN,
        maxValue = DateTimeDefinition.MAX_IN_MILLIS,
        default = LocalDateTime(1971, 1, 12, 13, 34, 22)
    )

    @Test
    fun createNowDateTime() {
        val now = DateTimeDefinition.nowUTC().toInstant(UTC).toEpochMilliseconds()
        val expected = Clock.System.now().toEpochMilliseconds()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -100..100
        }
    }

    @Test
    fun convertValuesWithMillisecondsPrecisionToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (dateTime in dateTimesMillisToTest) {
            bc.reserve(
                defMilli.calculateStorageByteLength(dateTime)
            )
            defMilli.writeStorageBytes(dateTime, bc::write)
            expect(dateTime) {
                defMilli.readStorageBytes(bc.size, bc::read)
            }
            bc.reset()
        }
    }

    @Test
    fun convertValuesWithSecondsPrecisionToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (dateTime in dateTimesSecondsToTest) {
            bc.reserve(
                def.calculateStorageByteLength(dateTime)
            )
            def.writeStorageBytes(dateTime, bc::write)
            expect(dateTime) {
                def.readStorageBytes(bc.size, bc::read)
            }
            bc.reset()
        }
    }

    @Test
    fun convertValuesWithNanosPrecisionToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (dateTime in dateTimesNanosToTest) {
            bc.reserve(
                defNano.calculateStorageByteLength(dateTime)
            )
            defNano.writeStorageBytes(dateTime, bc::write)
            expect(dateTime) {
                defNano.readStorageBytes(bc.size, bc::read)
            }
            bc.reset()
        }
    }

    @Test
    fun convertValuesWithMillisPrecisionToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (dateTime in dateTimesSecondsToTest) {
            bc.reserve(defMilli.calculateTransportByteLength(dateTime, cacheFailer))
            defMilli.writeTransportBytes(dateTime, cacheFailer, bc::write)
            expect(dateTime) {
                defMilli.readTransportBytes(bc.size, bc::read)
            }
            bc.reset()
        }
    }

    @Test
    fun convertValuesWithSecondsPrecisionToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (dateTime in dateTimesSecondsToTest) {
            bc.reserve(def.calculateTransportByteLength(dateTime, cacheFailer))
            def.writeTransportBytes(dateTime, cacheFailer, bc::write)
            expect(dateTime) { def.readTransportBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesWithNanosPrecisionToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (dateTime in dateTimesNanosToTest) {
            bc.reserve(defNano.calculateTransportByteLength(dateTime, cacheFailer))
            defNano.writeTransportBytes(dateTime, cacheFailer, bc::write)
            expect(dateTime) { defNano.readTransportBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (dateTime in dateTimesNanosToTest) {
            val b = def.asString(dateTime)
            expect(dateTime) { def.fromString(b) }
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
        checkProtoBufConversion(this.def, DateTimeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, DateTimeDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, DateTimeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, DateTimeDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, DateTimeDefinition.Model)

        expect(
            """
            required: false
            final: true
            unique: true
            precision: MILLIS
            minValue: '-999999-01-01T00:00'
            maxValue: '+999999-12-31T23:59:59.999'
            default: '1971-01-12T13:34:22'

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, DateTimeDefinition.Model)
        }
    }
}
