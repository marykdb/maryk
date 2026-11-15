package maryk.datastore.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UniqueClaimReconciliationTest {
    @Test
    fun rotatingCandidatesRetainContentEqualClaimsAndTheirQualifierIdentity() {
        val before = listOf(CurrentUniqueClaim(byteArrayOf(1), listOf(byteArrayOf(2), byteArrayOf(3))),
            CurrentUniqueClaim(byteArrayOf(4), listOf(byteArrayOf(3))))
        val after = listOf(CurrentUniqueClaim(byteArrayOf(1), listOf(byteArrayOf(3), byteArrayOf(5))))
        val removed = obsoleteUniqueClaims(before, after)
        assertEquals(2, removed.size)
        assertContentEquals(byteArrayOf(1, 2), removed[0].storageKey)
        assertContentEquals(byteArrayOf(4, 3), removed[1].storageKey)
        assertContentEquals(byteArrayOf(4), removed[1].reference)
        assertContentEquals(byteArrayOf(3), removed[1].value)
        assertContentEquals(byteArrayOf(2), before[0].candidates[0])
    }

    @Test
    fun collectionsAndCompleteRemovalUseTheSameContentIdentity() {
        val before = List(128) { CurrentUniqueClaim(byteArrayOf(1, it.toByte()), listOf(byteArrayOf(7))) }
        val after = before.filterIndexed { index, _ -> index % 2 == 0 }.map {
            CurrentUniqueClaim(it.reference.copyOf(), it.candidates.map(ByteArray::copyOf))
        }
        val removed = obsoleteUniqueClaims(before, after)
        assertEquals(64, removed.size)
        removed.forEachIndexed { index, claim ->
            assertContentEquals(byteArrayOf(1, (index * 2 + 1).toByte(), 7), claim.storageKey)
        }
        assertEquals(128, obsoleteUniqueClaims(before, emptyList()).size)
        assertTrue(obsoleteUniqueClaims(emptyList(), after).isEmpty())
        assertTrue(obsoleteUniqueClaims(before, before).isEmpty())
    }
}
