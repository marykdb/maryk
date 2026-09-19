package maryk.core.query.responses

import maryk.core.query.orders.Direction.ASC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DataFetchTypeTest {
    @Test
    fun fetchByTableScanCopiesKeys() {
        val start = byteArrayOf(1, 2)
        val stop = byteArrayOf(3, 4)
        val fetch = FetchByTableScan(ASC, start, stop)
        val hash = fetch.hashCode()

        start[0] = 9
        stop[0] = 9
        fetch.startKey!![0] = 8
        fetch.stopKey!![0] = 8

        assertEquals(FetchByTableScan(ASC, byteArrayOf(1, 2), byteArrayOf(3, 4)), fetch)
        assertEquals(hash, fetch.hashCode())
        assertNotEquals(FetchByTableScan(ASC, start, stop), fetch)

        val (_, copiedStart, copiedStop) = fetch
        copiedStart!![0] = 7
        copiedStop!![0] = 7
        assertEquals(FetchByTableScan(ASC, byteArrayOf(1, 2), byteArrayOf(3, 4)), fetch.copy())
    }

    @Test
    fun fetchByIndexScanCopiesKeys() {
        val index = byteArrayOf(1)
        val start = byteArrayOf(2)
        val stop = byteArrayOf(3)
        val fetch = FetchByIndexScan(index, ASC, start, stop)
        val hash = fetch.hashCode()

        index[0] = 9
        start[0] = 9
        stop[0] = 9
        fetch.index!![0] = 8
        fetch.startKey!![0] = 8
        fetch.stopKey!![0] = 8

        assertEquals(FetchByIndexScan(byteArrayOf(1), ASC, byteArrayOf(2), byteArrayOf(3)), fetch)
        assertEquals(hash, fetch.hashCode())
        assertNotEquals(FetchByIndexScan(index, ASC, start, stop), fetch)

        val (copiedIndex, _, copiedStart, copiedStop) = fetch
        copiedIndex!![0] = 7
        copiedStart!![0] = 7
        copiedStop!![0] = 7
        assertEquals(FetchByIndexScan(byteArrayOf(1), ASC, byteArrayOf(2), byteArrayOf(3)), fetch.copy())
    }

    @Test
    fun fetchByUniqueKeyCopiesIndex() {
        val index = byteArrayOf(1)
        val fetch = FetchByUniqueKey(index)
        val hash = fetch.hashCode()

        index[0] = 9
        fetch.uniqueIndex[0] = 8

        assertEquals(FetchByUniqueKey(byteArrayOf(1)), fetch)
        assertEquals(hash, fetch.hashCode())
        assertNotEquals(FetchByUniqueKey(index), fetch)

        val (copiedIndex) = fetch
        copiedIndex[0] = 7
        assertEquals(FetchByUniqueKey(byteArrayOf(1)), fetch.copy())
    }
}
