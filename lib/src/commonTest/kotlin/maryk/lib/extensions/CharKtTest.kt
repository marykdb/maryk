package maryk.lib.extensions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharKtTest {
    @Test
    fun testIsLineBreak() {
        assertTrue { '\n'.isLineBreak() }
        assertTrue { '\r'.isLineBreak() }
        assertFalse { ' '.isLineBreak() }
    }

    @Test
    fun testIsSpacing() {
        assertTrue { ' '.isSpacing() }
        assertTrue { '\t'.isSpacing() }
        assertFalse { '\n'.isSpacing() }
    }

    @Test
    fun testIsLowerHexChar() {
        assertTrue { '0'.isLowerHexChar() }
        assertTrue { '9'.isLowerHexChar() }
        assertTrue { 'a'.isLowerHexChar() }
        assertTrue { 'f'.isLowerHexChar() }
        assertFalse { 'A'.isLowerHexChar() }
        assertFalse { 'g'.isLowerHexChar() }
    }
}
