package maryk.datastore.remote

import maryk.core.query.DefinitionsConversionContext
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
    fun readLengthPrefixDecodesBigEndian() {
        val result = RemoteStoreCodec.readLengthPrefix(byteArrayOf(0x00, 0x00, 0x01, 0x00), 0)
        assertEquals(256, result?.length)
        assertEquals(4, result?.nextOffset)
    }
}
