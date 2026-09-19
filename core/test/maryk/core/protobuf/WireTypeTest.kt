package maryk.core.protobuf

import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WireTypeTest {
    @Test
    fun testUnknownWireType() {
        assertFailsWith<ParseException> {
            wireTypeOf(9)
        }
    }
}
