package maryk.test

inline fun <reified T> shouldThrow(thunk: () -> Any?): T {
    val throwable = try {
        thunk()
        null
    } catch (e: Throwable) {
        e
    }

    when (throwable) {
        null -> throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
        !is T -> throw throwable
        else -> return throwable
    }
}
