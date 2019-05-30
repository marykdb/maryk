package maryk.lib.uuid

import kotlin.test.Test
import kotlin.test.assertNotEquals

class UUIDKtTest {
    @Test
    fun testGenerateUUID() {
        val uuid = generateUUID()

        assertNotEquals(0, uuid.first)
        assertNotEquals(0, uuid.second)
    }
}
