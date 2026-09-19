package maryk.datastore.remote

import kotlinx.coroutines.CancellationException
import maryk.core.models.IsObjectDataModel
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteStoreCodecTest {
    @Test
    fun lengthPrefixRejectsNegativeLength() {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteStoreCodec.lengthPrefix(-1)
        }
        assertTrue(exception.message?.contains("negative") == true)
    }

    @Test
    fun readLengthPrefixRejectsNegativeOffset() {
        assertNull(RemoteStoreCodec.readLengthPrefix(byteArrayOf(0, 0, 0, 1), -1))
    }

    @Test
    fun readLengthPrefixRejectsOverflowOffset() {
        assertNull(RemoteStoreCodec.readLengthPrefix(byteArrayOf(0, 0, 0, 1), Int.MAX_VALUE))
    }

    @Test
    fun decodeRejectsTrailingBytes() {
        val info = RemoteStoreInfo(
            definitions = RemoteDataStore.collectDefinitions(emptyList()),
            modelIds = emptyList(),
            keepAllVersions = true,
            supportsFuzzyQualifierFiltering = false,
            supportsSubReferenceFiltering = false,
        )
        val encoded = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
        val withTrailing = encoded + byteArrayOf(0x01)

        val exception = assertFailsWith<IllegalStateException> {
            RemoteStoreCodec.decode(RemoteStoreInfo.Serializer, withTrailing, DefinitionsConversionContext())
        }
        assertTrue(exception.message?.contains("trailing bytes") == true)
    }

    @Test
    fun encodeRejectsPayloadAboveMaxBeforeAllocation() {
        val info = RemoteStoreInfo(
            definitions = RemoteDataStore.collectDefinitions(emptyList()),
            modelIds = emptyList(),
            keepAllVersions = true,
            supportsFuzzyQualifierFiltering = false,
            supportsSubReferenceFiltering = false,
        )

        val exception = assertFailsWith<IllegalStateException> {
            RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext(), maxBytes = 0)
        }
        assertTrue(exception.message?.contains("exceeds max size") == true)
    }

    @Test
    fun encodeRejectsNegativeCalculatedLengthBeforeAllocation() {
        val exception = assertFailsWith<IllegalStateException> {
            RemoteStoreCodec.encode(NegativeLengthSerializer, Any(), null)
        }

        assertTrue(exception.message?.contains("negative") == true)
    }

    @Test
    fun encodeRejectsWritesPastCalculatedLength() {
        val exception = assertFailsWith<IllegalStateException> {
            RemoteStoreCodec.encode(OverwritingSerializer, Any(), null)
        }

        assertTrue(exception.message?.contains("attempted to write past") == true)
    }

    @Test
    fun decodeDoesNotWrapFatalErrors() {
        assertFailsWith<OutOfMemoryError> {
            RemoteStoreCodec.decode(FatalReadSerializer, byteArrayOf(1), null)
        }
    }

    @Test
    fun decodeDoesNotWrapCancellation() {
        assertFailsWith<CancellationException> {
            RemoteStoreCodec.decode(CancellingReadSerializer, byteArrayOf(1), null)
        }
    }

    @Test
    fun readLengthPrefixDecodesBigEndian() {
        val result = RemoteStoreCodec.readLengthPrefix(byteArrayOf(0x00, 0x00, 0x01, 0x00), 0)
        assertEquals(256, result?.length)
        assertEquals(4, result?.nextOffset)
    }

    @Test
    fun remoteStoreInfoPreservesLegacyFieldNumbers() {
        assertEquals(3u, RemoteStoreInfo.keepAllVersions.index)
        assertEquals(4u, RemoteStoreInfo.supportsFuzzyQualifierFiltering.index)
        assertEquals(5u, RemoteStoreInfo.supportsSubReferenceFiltering.index)
        assertEquals(6u, RemoteStoreInfo.keepUpdateHistoryIndex.index)
    }
}

private object NegativeLengthSerializer : ThrowingSerializer() {
    override fun calculateObjectProtoBufLength(dataObject: Any, cacher: WriteCacheWriter, context: IsPropertyContext?) = -1
}

private object OverwritingSerializer : ThrowingSerializer() {
    override fun calculateObjectProtoBufLength(dataObject: Any, cacher: WriteCacheWriter, context: IsPropertyContext?) = 0

    override fun writeObjectProtoBuf(
        dataObject: Any,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?,
    ) {
        writer(1)
    }
}

private object FatalReadSerializer : ThrowingSerializer() {
    override fun readProtoBuf(length: Int, reader: () -> Byte, context: IsPropertyContext?): ObjectValues<Any, IsObjectDataModel<Any>> {
        throw OutOfMemoryError("fatal")
    }
}

private object CancellingReadSerializer : ThrowingSerializer() {
    override fun readProtoBuf(length: Int, reader: () -> Byte, context: IsPropertyContext?): ObjectValues<Any, IsObjectDataModel<Any>> {
        throw CancellationException("cancelled")
    }
}

private abstract class ThrowingSerializer : IsObjectDataModelSerializer<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext> {
    override fun writeObjectAsJson(
        obj: Any,
        writer: IsJsonLikeWriter,
        context: IsPropertyContext?,
        skip: List<IsDefinitionWrapper<*, *, *, Any>>?,
    ) = throw UnsupportedOperationException()

    override fun calculateObjectProtoBufLength(dataObject: Any, cacher: WriteCacheWriter, context: IsPropertyContext?): Int =
        throw UnsupportedOperationException()

    override fun writeObjectProtoBuf(
        dataObject: Any,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?,
    ): Unit = throw UnsupportedOperationException()

    override fun transformContext(context: IsPropertyContext?): IsPropertyContext? = context

    override fun getValueWithDefinition(
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, Any>,
        obj: Any,
        context: IsPropertyContext?,
    ): Any? = throw UnsupportedOperationException()

    override fun calculateProtoBufLength(
        values: ObjectValues<Any, IsObjectDataModel<Any>>,
        cacher: WriteCacheWriter,
        context: IsPropertyContext?,
    ): Int = throw UnsupportedOperationException()

    override fun writeProtoBuf(
        values: ObjectValues<Any, IsObjectDataModel<Any>>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?,
    ) = throw UnsupportedOperationException()

    override fun readProtoBuf(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
    ): ObjectValues<Any, IsObjectDataModel<Any>> = throw UnsupportedOperationException()

    override fun writeJson(
        values: ObjectValues<Any, IsObjectDataModel<Any>>,
        context: IsPropertyContext?,
        pretty: Boolean,
    ): String = throw UnsupportedOperationException()

    override fun writeJson(
        values: ObjectValues<Any, IsObjectDataModel<Any>>,
        writer: IsJsonLikeWriter,
        context: IsPropertyContext?,
    ) = throw UnsupportedOperationException()

    override fun readJson(json: String, context: IsPropertyContext?): ObjectValues<Any, IsObjectDataModel<Any>> =
        throw UnsupportedOperationException()

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): ObjectValues<Any, IsObjectDataModel<Any>> =
        throw UnsupportedOperationException()
}
