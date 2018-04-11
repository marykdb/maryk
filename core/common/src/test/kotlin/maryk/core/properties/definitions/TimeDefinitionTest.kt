package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.TimePrecision
import maryk.lib.exceptions.ParseException
import maryk.lib.time.Instant
import maryk.lib.time.Time
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

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
        indexed = true,
        required = false,
        final = true,
        searchable = false,
        unique = true,
        minValue = Time.MIN,
        maxValue = Time.MAX_IN_MILLIS,
        fillWithNow = true,
        precision = TimePrecision.MILLIS
    )

    @Test
    fun create_now_time() {
        val expected = Instant.getCurrentEpochTimeInMillis()% (24 * 60 * 60 * 1000) / 1000
        val now = def.createNow().toSecondsOfDay()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -1..1
        }
    }

    @Test
    fun convert_millisecond_precision_values_to_storage_bytes_and_back() {
        val bc = ByteCollector()
        for (it in arrayOf(Time.MAX_IN_MILLIS, Time.MIN)) {
            bc.reserve(
                defMilli.calculateStorageByteLength(it)
            )
            defMilli.writeStorageBytes(it, bc::write)
            defMilli.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_seconds_precision_values_to_storage_bytes_and_back() {
        val bc = ByteCollector()
        for (it in timesToTestSeconds) {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_seconds_precision_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (it in timesToTestSeconds) {
            bc.reserve(def.calculateTransportByteLength(it, cacheFailer))
            def.writeTransportBytes(it, cacheFailer, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_millis_precision_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (it in timesToTestMillis) {
            bc.reserve(defMilli.calculateTransportByteLength(it, cacheFailer))
            defMilli.writeTransportBytes(it, cacheFailer, bc::write)
            defMilli.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_values_to_String_and_back() {
        for (it in timesToTestMillis) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalid_String_value_should_throw_exception() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, TimeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, TimeDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, TimeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, TimeDefinition.Model)
    }

    @Test
    fun read_native_times_to_time() {
        this.def.fromNativeType(12345L) shouldBe Time(3, 25, 45)
        this.def.fromNativeType(12346) shouldBe Time(3, 25, 46)
    }
}
