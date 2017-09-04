package maryk.core.extensions

import io.kotlintest.matchers.shouldBe
import org.junit.Test

internal class RandomKtTest {
    @Test
    fun randomBytes() {
        randomBytes(7).size shouldBe 7
    }
}