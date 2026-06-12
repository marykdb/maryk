package maryk.datastore.foundationdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.writeVarBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetListTest {
    @Test
    fun readsStoredListCountWithBoundsChecks() {
        val storedCount = buildList {
            repeat(VERSION_BYTE_SIZE) { add(0.toByte()) }
            3.writeVarBytes { add(it) }
        }.toByteArray()

        assertEquals(3, readStoredListCount(storedCount))

        assertFailsWith<StorageException> {
            readStoredListCount(ByteArray(VERSION_BYTE_SIZE - 1))
        }
        assertFailsWith<StorageException> {
            readStoredListCount(ByteArray(VERSION_BYTE_SIZE))
        }
        assertFailsWith<StorageException> {
            requireVersionedValue(ByteArray(VERSION_BYTE_SIZE - 1))
        }
    }
}
