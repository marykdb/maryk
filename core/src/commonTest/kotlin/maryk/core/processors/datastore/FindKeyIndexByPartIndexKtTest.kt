package maryk.core.processors.datastore

import maryk.test.shouldBe
import kotlin.test.Test

class FindKeyIndexByPartIndexKtTest {
    val bytes = byteArrayOf(
        /* index 0: */ 2, 0, 0,
        /* index 1: */ 5, 0, 0, 0, 0, 0,
        /* index 2: */ 1, 0,
        /* index 3: */ 4, 0, 0, 0, 0,
        /* index 4: */ 8, 0, 0, 0, 0, 0, 0, 0, 0,
        /* index 5: */ 1, 0
    )

    @Test
    fun findKeyIndices() {
        findByteIndexByPartIndex(0, bytes) shouldBe 1
        findByteIndexByPartIndex(1, bytes) shouldBe 4
        findByteIndexByPartIndex(2, bytes) shouldBe 10
        findByteIndexByPartIndex(3, bytes) shouldBe 12
        findByteIndexByPartIndex(4, bytes) shouldBe 17
        findByteIndexByPartIndex(5, bytes) shouldBe 26
    }

    @Test
    fun findKeyIndicesAndSizes() {
        findByteIndexAndSizeByPartIndex(0, bytes) shouldBe Pair(1, 2)
        findByteIndexAndSizeByPartIndex(1, bytes) shouldBe Pair(4, 5)
        findByteIndexAndSizeByPartIndex(2, bytes) shouldBe Pair(10, 1)
        findByteIndexAndSizeByPartIndex(3, bytes) shouldBe Pair(12, 4)
        findByteIndexAndSizeByPartIndex(4, bytes) shouldBe Pair(17, 8)
        findByteIndexAndSizeByPartIndex(5, bytes) shouldBe Pair(26, 1)
    }
}
