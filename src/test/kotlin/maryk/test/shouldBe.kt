package maryk.test

import kotlin.test.assertEquals

infix fun <T> T.shouldBe(expected: Any?)
        = assertEquals(expected, this, "expected: $expected but was: $this")
