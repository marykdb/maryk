package maryk.test

import kotlin.test.assertNotEquals

infix fun <T> T.shouldNotBe(notExpected: Any?) =
    assertNotEquals(notExpected, this, "Should not be but was: $notExpected")
