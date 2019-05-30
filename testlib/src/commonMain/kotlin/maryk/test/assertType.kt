package maryk.test

import kotlin.test.assertTrue

inline fun <reified T> assertType(value: Any): T {
    assertTrue("expected type: ${T::class.simpleName} but was: $value") {
        value is T
    }
    return value as T
}
