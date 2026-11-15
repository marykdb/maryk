package maryk.datastore.foundationdb

import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packHistoricTableKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class HistoricKeyPackingTest {
    @Test
    fun directPackingRetainsReleasedEscapingAndReversedVersion() {
        assertContentEquals(
            byteArrayOf(2, 0, 1, 0, 3, 1, 1, 1, 2, 2, -1, 0, -1, -2, -3, -4, 127, 2, 1, 0),
            packHistoricTableKey(byteArrayOf(2, 0), byteArrayOf(1, 0, 3), byteArrayOf(0, 1, 2, -1),
                byteArrayOf(0, 1, 2, 3, -128, -3, -2, -1)),
        )
        val random = Random(912)
        val qualifiers = listOf(byteArrayOf(), ByteArray(256) { it.toByte() }, ByteArray(1024)) +
            List(128) { random.nextBytes(it * 3) }
        for (qualifier in qualifiers) {
            val directory = random.nextBytes(7)
            val key = random.nextBytes(16)
            val version = random.nextBytes(8)
            val before = listOf(directory.copyOf(), key.copyOf(), qualifier.copyOf(), version.copyOf())
            assertContentEquals(packVersionedKey(directory, key, encodeZeroFreeUsing01(qualifier), version = version),
                packHistoricTableKey(directory, key, qualifier, version))
            listOf(directory, key, qualifier, version).forEachIndexed { index, bytes -> assertContentEquals(before[index], bytes) }
        }
    }
}
