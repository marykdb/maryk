package maryk.datastore.rocksdb.processors.helpers

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DeleteUniqueIndexValueTest {
    @Test
    fun checkedRocksDbByteLengthRejectsOverflow() {
        assertFailsWith<IllegalArgumentException> {
            Int.MAX_VALUE.checkedRocksDbByteLengthPlus(1)
        }
    }

    @Test
    fun checkedRocksDbByteLengthRejectsNegativeAddend() {
        assertFailsWith<IllegalArgumentException> {
            0.checkedRocksDbByteLengthPlus(-1)
        }
    }

    @Test
    fun valueRangeRejectsInvalidRangesBeforeAllocation() {
        assertFailsWith<IllegalArgumentException> {
            requireValueRange(valueSize = 1, offset = 0, length = Int.MAX_VALUE)
        }

        assertFailsWith<IllegalArgumentException> {
            requireValueRange(valueSize = 1, offset = -1, length = 1)
        }
    }
}
