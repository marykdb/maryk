package maryk.core.protobuf

import maryk.lib.exceptions.ParseException
import maryk.test.shouldThrow
import kotlin.test.Test

class WireTypeTest {
    @Test
    fun testUnknownWireType() {
        shouldThrow<ParseException> {
            wireTypeOf(9)
        }
    }
}
