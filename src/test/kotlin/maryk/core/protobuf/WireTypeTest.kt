package maryk.core.protobuf

import maryk.core.properties.exceptions.ParseException
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