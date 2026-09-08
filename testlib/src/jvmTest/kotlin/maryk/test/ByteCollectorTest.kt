package maryk.test

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteCollectorTest {
    @Test
    fun reserveResetsCursorsWhenReusingTheSameBuffer() {
        val collector = ByteCollector()
        collector.reserve(1)
        collector.write(1)
        assertEquals(1, collector.read())

        collector.reserve(1)
        collector.write(2)

        assertEquals(2, collector.read())
    }
}
