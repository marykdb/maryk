package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.TimePrecision
import maryk.lib.exceptions.ParseException
import maryk.lib.time.DateTime
import maryk.lib.time.Instant
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

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
        maxValue = DateTime.MAX_IN_MILLIS,
        default = DateTime(1971, 1, 12, 13, 34, 22)
    )

    @Test
    fun create_now_date_time() {
        val now = def.createNow().toEpochMilli()
        val expected = Instant.getCurrentEpochTimeInMillis()

        assertTrue("$now is diverging too much from $expected time") {
            expected - now in -100..100
        }
    }

    @Test
    fun convert_values_with_milliseconds_precision_to_storage_bytes_and_back() {
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
    fun convert_values_with_seconds_precision_to_storage_bytes_and_back() {
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
    fun convert_values_with_seconds_precision_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for(it in arrayOf(DateTime.MIN, DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            bc.reserve(defMilli.calculateTransportByteLength(it, cacheFailer))
            defMilli.writeTransportBytes(it, cacheFailer, bc::write)
            defMilli.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_values_with_millisecond_precision_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            bc.reserve(def.calculateTransportByteLength(it, cacheFailer))
            def.writeTransportBytes(it, cacheFailer, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_values_to_String_and_back() {
        for (it in dateTimesToTest) {
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
        checkProtoBufConversion(this.def, DateTimeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, DateTimeDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, DateTimeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, DateTimeDefinition.Model)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, DateTimeDefinition.Model)
        checkYamlConversion(this.defMaxDefined, DateTimeDefinition.Model) shouldBe """
        indexed: true
        searchable: false
        required: false
        final: true
        unique: true
        precision: MILLIS
        minValue: '-99999999-01-01T00:00'
        maxValue: '99999999-12-31T23:59:59.999'
        default: '1971-01-12T13:34:22'
        fillWithNow: true

        """.trimIndent()
    }
}
