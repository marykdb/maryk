package maryk.core.processors.datastore

import maryk.core.extensions.bytes.toVarBytes
import maryk.lib.exceptions.ParseException
import maryk.test.models.CaseInsensitivePerson
import maryk.test.models.CaseInsensitivePerson.firstName
import maryk.test.models.CaseInsensitivePerson.surname
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class FindKeyIndexByPartIndexKtTest {
    val bytes = byteArrayOf(
        /* index 0: */ 0, 0, 0, 1,
        /* index 1: */ 0, 0, 0, 0, 0, 1,
        /* index 2: */ 0, 1,
        /* index 3: */ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
        /* index 4: */ 0, 0, 0, 0, 0, 0, 0, 0, 1,
        /* index 5: */ 0, 1,
        /* index sizes */ 2, 9, 17, 2, 6, 4,
        /* key: */ 2, 2, 2, 2, 2, 2, 2, 2
    )

    @Test
    fun findKeyIndices() {
        expect(0) { findByteIndexByPartIndex(0, bytes, 8, indexPartCount = 6) }
        expect(4) { findByteIndexByPartIndex(1, bytes, 8, indexPartCount = 6) }
        expect(10) { findByteIndexByPartIndex(2, bytes, 8, indexPartCount = 6) }
        expect(12) { findByteIndexByPartIndex(3, bytes, 8, indexPartCount = 6) }
        expect(29) { findByteIndexByPartIndex(4, bytes, 8, indexPartCount = 6) }
        expect(38) { findByteIndexByPartIndex(5, bytes, 8, indexPartCount = 6) }
    }

    @Test
    fun findKeyIndicesAndSizes() {
        expect(Pair(0, 4)) { findByteIndexAndSizeByPartIndex(0, bytes, 8, indexPartCount = 6) }
        expect(Pair(4, 6)) { findByteIndexAndSizeByPartIndex(1, bytes, 8, indexPartCount = 6) }
        expect(Pair(10, 2)) { findByteIndexAndSizeByPartIndex(2, bytes, 8, indexPartCount = 6) }
        expect(Pair(12, 17)) { findByteIndexAndSizeByPartIndex(3, bytes, 8, indexPartCount = 6) }
        expect(Pair(29, 9)) { findByteIndexAndSizeByPartIndex(4, bytes, 8, indexPartCount = 6) }
        expect(Pair(38, 2)) { findByteIndexAndSizeByPartIndex(5, bytes, 8, indexPartCount = 6) }
    }

    @Test
    fun findKeyIndicesWithMultiByteLengthsAndSuffix() {
        val part0 = byteArrayOf(1)
        val part1 = ByteArray(128) { 2 }
        val part2 = ByteArray(130) { 3 }
        val key = byteArrayOf(9, 9, 9)
        val suffix = byteArrayOf(8, 8, 8, 8, 8)
        val bytes = part0 +
            part1 +
            part2 +
            130.toVarBytes() +
            128.toVarBytes() +
            1.toVarBytes() +
            key +
            suffix

        val sourceEnd = bytes.size - suffix.size

        expect(0) { findByteIndexByPartIndex(0, bytes, key.size, sourceEnd = sourceEnd, indexPartCount = 3) }
        expect(1) { findByteIndexByPartIndex(1, bytes, key.size, sourceEnd = sourceEnd, indexPartCount = 3) }
        expect(129) { findByteIndexByPartIndex(2, bytes, key.size, sourceEnd = sourceEnd, indexPartCount = 3) }

        expect(0 to 1) { findByteIndexAndSizeByPartIndex(0, bytes, key.size, sourceEnd = sourceEnd, indexPartCount = 3) }
        expect(1 to 128) { findByteIndexAndSizeByPartIndex(1, bytes, key.size, sourceEnd = sourceEnd, indexPartCount = 3) }
        expect(129 to 130) { findByteIndexAndSizeByPartIndex(2, bytes, key.size, sourceEnd = sourceEnd, indexPartCount = 3) }
    }

    @Test
    fun findKeyIndicesForNormalizedCompositeIndex() {
        val person = CaseInsensitivePerson.create {
            firstName with "José"
            surname with "García-López"
        }
        val key = ByteArray(CaseInsensitivePerson.Meta.keyByteSize)
        val indexBytes = CaseInsensitivePerson.Meta.indexes!![0].toStorageByteArrayForIndex(person, key)
            ?: error("Expected index bytes")

        expect(0 to "garcialopez".encodeToByteArray().size) {
            findByteIndexAndSizeByPartIndex(0, indexBytes, key.size, indexPartCount = 2)
        }
        expect("garcialopez".encodeToByteArray().size to "José".encodeToByteArray().size) {
            findByteIndexAndSizeByPartIndex(1, indexBytes, key.size, indexPartCount = 2)
        }
    }

    @Test
    fun malformedCompositeIndexDoesNotFallbackToShorterPartCount() {
        val bytes = "abcd".encodeToByteArray() +
            2.toVarBytes() +
            byteArrayOf(9)

        assertFailsWith<ParseException> {
            findByteIndexAndSizeByPartIndex(0, bytes, 1, indexPartCount = 2)
        }
    }

    @Test
    fun malformedCompositeIndexDoesNotBypassValidationForFirstPartIndexLookup() {
        val bytes = "abcd".encodeToByteArray() +
            2.toVarBytes() +
            byteArrayOf(9)

        assertFailsWith<ParseException> {
            findByteIndexByPartIndex(0, bytes, 1, indexPartCount = 2)
        }
    }

}
