package maryk.lib.uuid

import maryk.test.shouldNotBe
import kotlin.test.Test

class UUIDKtTest {
    @Test
    fun testGenerateUUID() {
        val uuid = generateUUID()

        uuid.first shouldNotBe 0
        uuid.second shouldNotBe 0
    }
}
