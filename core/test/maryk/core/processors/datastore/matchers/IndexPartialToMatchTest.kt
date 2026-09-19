package maryk.core.processors.datastore.matchers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexPartialToMatchTest {
    private fun dynamicIndexBytes(value: String, keySize: Int = 23): ByteArray {
        val valueBytes = value.encodeToByteArray()
        return valueBytes + byteArrayOf(valueBytes.size.toByte()) + ByteArray(keySize)
    }

    @Test
    fun keyPartialToMatch() {
        val toMatch = byteArrayOf(1, 3, 5)
        assertTrue { IndexPartialToMatch(1, 2, 5, 1, toMatch).match(byteArrayOf(0, 0, 1, 3, 5)) }
        assertFalse { IndexPartialToMatch(1, 2, 5, 1, toMatch).match(byteArrayOf(0, 0, 9, 8, 7)) }
    }

    @Test
    fun keyPartialSizeToMatch() {
        assertTrue { IndexPartialSizeToMatch(0, null, 5, 1, 3).match(dynamicIndexBytes("abc", 5)) }
        assertFalse { IndexPartialSizeToMatch(0, null, 5, 1, 3).match(dynamicIndexBytes("abcd", 5)) }
    }

    @Test
    fun keyPartialSizeToMatchWithIgnoredSuffix() {
        val encoded = dynamicIndexBytes("abc", 5)
        val withSuffix = encoded + byteArrayOf(7, 8)
        assertTrue { IndexPartialSizeToMatch(0, null, 5, 1, 3).match(withSuffix, sourceEnd = encoded.size) }
    }

    @Test
    fun keyPartialRegExToMatch() {
        val encoded = dynamicIndexBytes("TestMarykModel", 5)
        assertTrue {
            IndexPartialToRegexMatch(0, 5, 1, Regex("^(TestMarykModel)$")).match(encoded, sourceEnd = encoded.size)
        }
    }

    @Test
    fun dynamicMatchersRespectNonZeroOffset() {
        val prefix = byteArrayOf(11, 12, 13)
        val encoded = dynamicIndexBytes("TestMarykModel", 5)
        val bytes = prefix + encoded

        assertTrue {
            IndexPartialSizeToMatch(0, null, 5, 1, "TestMarykModel".length)
                .match(bytes, offset = prefix.size, length = encoded.size - 5, sourceEnd = bytes.size)
        }
        assertTrue {
            IndexPartialToRegexMatch(0, 5, 1, Regex("^(TestMarykModel)$"))
                .match(bytes, offset = prefix.size, length = encoded.size - 5, sourceEnd = bytes.size)
        }
        assertTrue {
            IndexPartialToMatch(0, null, 5, 1, "TestMarykModel".encodeToByteArray())
                .match(bytes, offset = prefix.size, length = encoded.size - 5, sourceEnd = bytes.size)
        }
    }

    @Test
    fun keyPartialToBeBigger() {
        val toBeSmaller = byteArrayOf(1, 3)
        assertTrue { IndexPartialToBeBigger(1, 2, 5, 1, toBeSmaller, true).match(byteArrayOf(0, 0, 2, 3, 5)) }
        assertTrue { IndexPartialToBeBigger(1, 2, 5, 1, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 5, 5)) }
        assertFalse { IndexPartialToBeBigger(1, 2, 5, 1, toBeSmaller, true).match(byteArrayOf(0, 0, 0, 4, 5)) }
        assertFalse { IndexPartialToBeBigger(1, 2, 5, 1, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 1, 5)) }

        assertTrue { IndexPartialToBeBigger(1, 2, 5, 1, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 3, 5)) }
        assertFalse { IndexPartialToBeBigger(1, 2, 5, 1, toBeSmaller, false).match(byteArrayOf(0, 0, 1, 3, 5)) }
    }

    @Test
    fun keyPartialToBeSmaller() {
        val toBeBigger = byteArrayOf(1, 3)
        assertTrue { IndexPartialToBeSmaller(1, 2, 5, 1, toBeBigger, true).match(byteArrayOf(0, 0, 0, 4, 5)) }
        assertTrue { IndexPartialToBeSmaller(1, 2, 5, 1, toBeBigger, true).match(byteArrayOf(0, 0, 1, 1, 5)) }
        assertFalse { IndexPartialToBeSmaller(1, 2, 5, 1, toBeBigger, true).match(byteArrayOf(0, 0, 2, 3, 5)) }
        assertFalse { IndexPartialToBeSmaller(1, 2, 5, 1, toBeBigger, true).match(byteArrayOf(0, 0, 1, 5, 5)) }

        assertTrue { IndexPartialToBeSmaller(1, 2, 5, 1, toBeBigger, true).match(byteArrayOf(0, 0, 1, 3, 5)) }
        assertFalse { IndexPartialToBeSmaller(1, 2, 5, 1, toBeBigger, false).match(byteArrayOf(0, 0, 1, 3, 5)) }
    }

    @Test
    fun keyPartialToBeOneOf() {
        val toBeOneOf = listOf(
            byteArrayOf(0, 3),
            byteArrayOf(1, 2),
            byteArrayOf(5, 6)
        )

        assertTrue { IndexPartialToBeOneOf(1, 1, 3, 1, toBeOneOf).match(byteArrayOf(-1, 1, 2)) }
        assertFalse { IndexPartialToBeOneOf(1, 1, 3, 1, toBeOneOf).match(byteArrayOf(99, 3, 2)) }
        assertTrue { IndexPartialToBeOneOf(1, 1, 4, 1, toBeOneOf).match(byteArrayOf(5, 5, 6, 4)) }
    }

    @Test
    fun dynamicGreaterThanTreatsLongerEqualPrefixAsBigger() {
        val bytes = dynamicIndexBytes("Jannes")
        assertTrue {
            IndexPartialToBeBigger(0, null, 23, 1, "Jan".encodeToByteArray(), false)
                .match(bytes, sourceEnd = bytes.size)
        }
    }

    @Test
    fun dynamicLessThanTreatsShorterEqualPrefixAsSmaller() {
        val bytes = dynamicIndexBytes("Jannes")
        assertTrue {
            IndexPartialToBeSmaller(0, null, 23, 1, "Jannesz".encodeToByteArray(), false)
                .match(bytes, sourceEnd = bytes.size)
        }
    }

    @Test
    fun dynamicValueInDoesNotMatchLongerEqualPrefix() {
        val bytes = dynamicIndexBytes("Jannes")
        assertFalse {
            IndexPartialToBeOneOf(0, null, 23, 1, listOf("Jan".encodeToByteArray()))
                .match(bytes, sourceEnd = bytes.size)
        }
    }
}
