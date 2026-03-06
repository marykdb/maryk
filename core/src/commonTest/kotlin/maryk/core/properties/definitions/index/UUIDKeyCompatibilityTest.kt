package maryk.core.properties.definitions.index

import kotlin.test.Test
import kotlin.test.assertSame

class UUIDKeyCompatibilityTest {
    @Suppress("DEPRECATION")
    @Test
    fun supportsDeprecatedAlias() {
        val key: UUIDKey = UUIDKey

        assertSame(UUIDv4Key, key)
    }
}
