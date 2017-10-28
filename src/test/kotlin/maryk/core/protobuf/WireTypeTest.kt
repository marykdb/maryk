package maryk.core.protobuf

import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

class WireTypeTest {
    @Test
    fun testUnknownWireType() {
        shouldThrow<ParseException> {
            wireTypeOf(9)
        }
    }
}