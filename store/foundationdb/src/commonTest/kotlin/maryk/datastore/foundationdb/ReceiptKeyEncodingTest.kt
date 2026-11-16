package maryk.datastore.foundationdb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.uuid.Uuid
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.tuple.Tuple

class ReceiptKeyEncodingTest {
    @Test
    fun matchesCanonicalTupleEncodingAndOwnsItsBytes() {
        val prefix = byteArrayOf(0, 1, -1)
        val session = Tuple.from("session").pack()
        val originalPrefix = prefix.copyOf()
        val originalSession = session.copyOf()
        val ids = listOf(
            "00000000-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
            "00112233-4455-6677-8899-aabbccddeeff",
        ).map { Uuid.parse(it) } + List(100) { Uuid.random() }
        for (id in ids) {
            val expected = packKey(prefix, session, Tuple.from(id.toString()).pack())
            val actual = packReceiptKey(prefix, session, id)
            assertContentEquals(expected, actual)
            actual.fill(0)
            assertContentEquals(originalPrefix, prefix)
            assertContentEquals(originalSession, session)
        }
    }
    @Test
    fun ordinalsMatchCanonicalUuidTupleAcrossEveryByteBoundary() {
        val prefix = byteArrayOf(0, 1, -1)
        val session = Tuple.from("owner").pack()
        val originalPrefix = prefix.copyOf()
        val originalSession = session.copyOf()
        val random = Random(343)
        val ordinals = listOf(0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -1L) +
            (0 until 64).flatMap { listOf(1L shl it, (1L shl it) - 1) } + List(256) { random.nextLong() }
        for (ordinal in ordinals) {
            val expected = packKey(prefix, session, Tuple.from(Uuid.fromLongs(0L, ordinal).toString()).pack())
            val actual = packReceiptKey(prefix, session, ordinal)
            assertContentEquals(expected, actual)
            actual.fill(0)
            assertContentEquals(originalPrefix, prefix)
            assertContentEquals(originalSession, session)
        }
    }

}
