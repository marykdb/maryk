package maryk.lib.extensions

import maryk.test.shouldBe
import kotlin.test.Test

internal class RandomKtTest {
    @Test
    fun randomBytes() {
        randomBytes(7).size shouldBe 7
    }
}
