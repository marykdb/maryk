package maryk.datastore.foundationdb.processors.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionBytesConvertersTest {
    @Test
    fun readsVersionBytesWithBoundsChecks() {
        val version = 123456789uL
        val bytes = version.toReversedVersionBytes()

        assertEquals(version, bytes.readReversedVersionBytes())

        assertFailsWith<IllegalArgumentException> {
            byteArrayOf(1, 2, 3).readVersionBytes()
        }
        assertFailsWith<IllegalArgumentException> {
            byteArrayOf(1, 2, 3).readReversedVersionBytes()
        }
        assertFailsWith<IllegalArgumentException> {
            bytes.readVersionBytes(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            bytes.readReversedVersionBytes(1)
        }
    }
}
