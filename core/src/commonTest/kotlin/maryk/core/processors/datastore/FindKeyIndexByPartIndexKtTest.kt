package maryk.core.processors.datastore

import kotlin.test.Test
import kotlin.test.expect

class FindKeyIndexByPartIndexKtTest {
    val bytes = byteArrayOf(
        /* index 0: */ 0, 0, 0, 1,
        /* index 1: */ 0, 0, 0, 0, 0, 1,
        /* index 2: */ 0, 1,
        /* index 3: */ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
        /* index 4: */ 0, 0, 0, 0, 0, 0, 0, 0, 1,
        /* index 5: */ 0, 1,
        /* index sizes */ 1, 8, 16, 1, 5, 3,
        /* key: */ 2, 2, 2, 2, 2, 2, 2, 2
    )

    @Test
    fun findKeyIndices() {
        expect(0) { findByteIndexByPartIndex(0, bytes, 8) }
        expect(4) { findByteIndexByPartIndex(1, bytes, 8) }
        expect(10) { findByteIndexByPartIndex(2, bytes, 8) }
        expect(12) { findByteIndexByPartIndex(3, bytes, 8) }
        expect(29) { findByteIndexByPartIndex(4, bytes, 8) }
        expect(38) { findByteIndexByPartIndex(5, bytes, 8) }
    }

    @Test
    fun findKeyIndicesAndSizes() {
        expect(Pair(0, 3)) { findByteIndexAndSizeByPartIndex(0, bytes, 8) }
        expect(Pair(4, 5)) { findByteIndexAndSizeByPartIndex(1, bytes, 8) }
        expect(Pair(10, 1)) { findByteIndexAndSizeByPartIndex(2, bytes, 8) }
        expect(Pair(12, 16)) { findByteIndexAndSizeByPartIndex(3, bytes, 8) }
        expect(Pair(29, 8)) { findByteIndexAndSizeByPartIndex(4, bytes, 8) }
        expect(Pair(38, 1)) { findByteIndexAndSizeByPartIndex(5, bytes, 8) }
    }
}
