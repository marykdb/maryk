package maryk.core.processors.datastore

import maryk.test.shouldBe
import kotlin.test.Test

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
        findByteIndexByPartIndex(0, bytes, 8) shouldBe 0
        findByteIndexByPartIndex(1, bytes, 8) shouldBe 4
        findByteIndexByPartIndex(2, bytes, 8) shouldBe 10
        findByteIndexByPartIndex(3, bytes, 8) shouldBe 12
        findByteIndexByPartIndex(4, bytes, 8) shouldBe 29
        findByteIndexByPartIndex(5, bytes, 8) shouldBe 38
    }

    @Test
    fun findKeyIndicesAndSizes() {
        findByteIndexAndSizeByPartIndex(0, bytes, 8) shouldBe Pair(0, 3)
        findByteIndexAndSizeByPartIndex(1, bytes, 8) shouldBe Pair(4, 5)
        findByteIndexAndSizeByPartIndex(2, bytes, 8) shouldBe Pair(10, 1)
        findByteIndexAndSizeByPartIndex(3, bytes, 8) shouldBe Pair(12, 16)
        findByteIndexAndSizeByPartIndex(4, bytes, 8) shouldBe Pair(29, 8)
        findByteIndexAndSizeByPartIndex(5, bytes, 8) shouldBe Pair(38, 1)
    }
}
