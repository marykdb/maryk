package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.RootDataModel
import maryk.core.models.migration.hasLegacyTransportStorage
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
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

private object NanosMapModel : RootDataModel<NanosMapModel>() {
    val stamps by map(1u, keyDefinition = DateTimeDefinition(precision = NANOS), valueDefinition = DateTimeDefinition(precision = NANOS))
}

class DateTimeNanosWireTest {
    @Test
    fun legacyStoredCollectionDefaultsUseTheirDefinitionsContext() {
        // Field 5 declares NANOS DateTime; field 6 is a released packed default.
        val legacy = byteArrayOf(42, 4, 26, 2, 32, 4, 50, 2, 8, 1)
        val timestamp = LocalDateTime(1970, 1, 1, 0, 0, 8, 1)
        val context = DefinitionsConversionContext()
        var offset = 0
        val list = context.readLegacyTransportStorage {
            ListDefinition.Model.Serializer.readProtoBuf(legacy.size, { legacy[offset++] }, ListDefinitionContext(context))
        }
        assertEquals(listOf(timestamp), list.toDataObject().default)
        val cache = WriteCache()
        val current = ByteArray(ListDefinition.Model.Serializer.calculateProtoBufLength(list, cache, ListDefinitionContext(context)))
        offset = 0
        ListDefinition.Model.Serializer.writeProtoBuf(list, cache, { current[offset++] = it }, ListDefinitionContext(context))
        offset = 0
        assertEquals(listOf(timestamp), ListDefinition.Model.Serializer.readProtoBuf(current.size, { current[offset++] }, ListDefinitionContext(context)).toDataObject().default)
        offset = 0
        val set = context.readLegacyTransportStorage {
            SetDefinition.Model.Serializer.readProtoBuf(legacy.size, { legacy[offset++] }, SetDefinitionContext(context))
        }
        assertEquals(setOf(timestamp), set.toDataObject().default)
    }

    @Test
    fun legacyPackedCollectionsRequireExplicitFormatContextAndRewriteToFramedElements() {
        // These pairs deliberately also form a valid new message (seconds=0, nanos=42).
        // Guessing from payload contents would silently collapse two values into one.
        val timestamps = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8), LocalDateTime(1970, 1, 1, 0, 0, 16, 42))
        val legacy = byteArrayOf(10, 4, 8, 0, 16, 42, 16, 42)
        val context = RequestContext(DefinitionsContext())
        assertEquals(true, NanosCollectionsModel.hasLegacyTransportStorage())
        var offset = 0
        val decoded = context.readLegacyTransportStorage {
            NanosCollectionsModel.Serializer.readProtoBuf(legacy.size, { legacy[offset++] }, context)
        }
        assertEquals(timestamps, decoded { stamps })
        assertEquals(42u, decoded { tail })
        val cache = WriteCache()
        val current = ByteArray(NanosCollectionsModel.Serializer.calculateProtoBufLength(decoded, cache))
        offset = 0
        NanosCollectionsModel.Serializer.writeProtoBuf(decoded, cache, { current[offset++] = it })
        offset = 0
        val reread = NanosCollectionsModel.Serializer.readProtoBuf(current.size, { current[offset++] }, context)
        assertEquals(timestamps, reread { stamps })
        assertEquals(42u, reread { tail })

        val set = SetDefinition(valueDefinition = DateTimeDefinition(precision = NANOS))
        offset = 2
        assertEquals(timestamps.toSet(), context.readLegacyTransportStorage {
            set.readTransportBytes(4, { legacy[offset++] }, context)
        })
    }

    @Test
    fun legacyMapKeysAndValuesKeepNanosecondPairsWithinTheirEntry() {
        val legacy = byteArrayOf(10, 6, 8, 8, 1, 16, 16, 42)
        val context = RequestContext(DefinitionsContext())
        var offset = 0
        val decoded = context.readLegacyTransportStorage {
            NanosMapModel.Serializer.readProtoBuf(legacy.size, { legacy[offset++] }, context)
        }
        assertEquals(mapOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1) to LocalDateTime(1970, 1, 1, 0, 0, 16, 42)), decoded { stamps })
    }

    @Test
    fun truncatedLegacyCollectionCannotReadTheFollowingFieldAndRestoresContext() {
        val context = RequestContext(DefinitionsContext())
        val truncated = byteArrayOf(10, 1, 8, 16, 42)
        var offset = 0
        assertFailsWith<ParseException> {
            context.readLegacyTransportStorage {
                NanosCollectionsModel.Serializer.readProtoBuf(truncated.size, { truncated[offset++] }, context)
            }
        }
        assertEquals(3, offset)
        val current = DateTimeDefinition(precision = NANOS).toTransportByteArray(LocalDateTime(1970, 1, 1, 0, 0))
        offset = 0
        assertEquals(LocalDateTime(1970, 1, 1, 0, 0), DateTimeDefinition(precision = NANOS).readTransportBytes(current.size, { current[offset++] }, context))
    }

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
