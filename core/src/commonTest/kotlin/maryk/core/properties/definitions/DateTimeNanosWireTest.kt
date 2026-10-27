package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.RootDataModel
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object NanosWireModel : RootDataModel<NanosWireModel>() {
    val stamp by dateTime(1u, precision = NANOS)
    val tail by number(2u, type = UInt32)
}

private object WithoutNanosWireModel : RootDataModel<WithoutNanosWireModel>() {
    val tail by number(2u, type = UInt32)
}

private object NanosCollectionsModel : RootDataModel<NanosCollectionsModel>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS))
    val tail by number(2u, type = UInt32)
}

class DateTimeNanosWireTest {
    @Test
    fun newNanosecondCollectionsUseFramedElements() {
        val timestamps = listOf(LocalDateTime(1970, 1, 1, 0, 0), LocalDateTime(1969, 12, 31, 23, 59, 59, 999_999_999))
        val values = NanosCollectionsModel.create { stamps with timestamps; tail with 42u }
        val cache = WriteCache()
        val bytes = ByteArray(NanosCollectionsModel.Serializer.calculateProtoBufLength(values, cache))
        var index = 0
        NanosCollectionsModel.Serializer.writeProtoBuf(values, cache, { bytes[index++] = it })
        assertEquals(10.toByte(), bytes[0])
        index = 0
        val decoded = NanosCollectionsModel.Serializer.readProtoBuf(bytes.size, { bytes[index++] })
        assertEquals(timestamps, decoded { stamps })
        assertEquals(42u, decoded { tail })
    }

    @Test
    fun legacyKnownScalarReadsButAlwaysWritesFramedOutput() {
        val timestamp = LocalDateTime(2026, 1, 1, 0, 0, 0, 1)
        val instant = timestamp.toInstant(UTC)
        val legacy = buildList {
            add(8.toByte())
            instant.epochSeconds.writeVarBytes(::add)
            instant.nanosecondsOfSecond.writeVarBytes(::add)
            add(16.toByte())
            add(42.toByte())
        }.toByteArray()
        var index = 0
        val values = NanosWireModel.Serializer.readProtoBuf(legacy.size, { legacy[index++] })
        assertEquals(timestamp, values { stamp })
        assertEquals(42u, values { tail })

        val cache = WriteCache()
        val bytes = ByteArray(NanosWireModel.Serializer.calculateProtoBufLength(values, cache))
        index = 0
        NanosWireModel.Serializer.writeProtoBuf(values, cache, { bytes[index++] = it })
        assertEquals(10.toByte(), bytes[0])
        index = 0
        val older = WithoutNanosWireModel.Serializer.readProtoBuf(bytes.size, { bytes[index++] })
        assertEquals(42u, older { tail })
    }

    @Test
    fun framedValuesRoundTripBeforeEpochAndAtNanosLimits() {
        val definition = DateTimeDefinition(precision = NANOS)
        for (timestamp in listOf(DateTimeDefinition.MIN, LocalDateTime(1969, 12, 31, 23, 59, 59, 999_999_999), DateTimeDefinition.MAX_IN_NANOS)) {
            val bytes = definition.toTransportByteArray(timestamp)
            var index = 0
            assertEquals(timestamp, definition.readTransportBytes(bytes.size, { bytes[index++] }, null, null))
            assertEquals(bytes.size, index)
        }
    }

    @Test
    fun malformedMessageCannotConsumeFollowingField() {
        val bytes = byteArrayOf(10, 2, 8, 0x80.toByte(), 16, 42)
        var index = 0
        assertFailsWith<ParseException> {
            NanosWireModel.Serializer.readProtoBuf(bytes.size, { bytes[index++] })
        }
        assertEquals(4, index)
    }

    @Test
    fun rejectsOutOfRangeNanoseconds() {
        val bytes = buildList {
            add(16.toByte())
            1_000_000_000.writeVarBytes(::add)
        }.toByteArray()
        var index = 0
        assertFailsWith<ParseException> {
            DateTimeDefinition(precision = NANOS).readTransportBytes(bytes.size, { bytes[index++] }, null, null)
        }
    }
}
