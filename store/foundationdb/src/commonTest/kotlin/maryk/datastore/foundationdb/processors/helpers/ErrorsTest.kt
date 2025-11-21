package maryk.datastore.foundationdb.processors.helpers

import kotlin.test.Test
import kotlin.test.assertSame

private class CompletionException(cause: Throwable?) : RuntimeException(null, cause)
private class ExecutionException(cause: Throwable?) : RuntimeException(null, cause)
private class MockFDBException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ErrorsTest {
    @Test
    fun unwrapsNestedFoundationDbExceptions() {
        val fdb = MockFDBException("boom")
        val wrapped = CompletionException(ExecutionException(fdb))

        assertSame(fdb, wrapped.unwrapFdb())
    }

    @Test
    fun unwrapsToUnderlyingNonFdbCause() {
        val cause = IllegalArgumentException("bad")
        val wrapped = ExecutionException(cause)

        assertSame(cause, wrapped.unwrapFdb())
    }

    @Test
    fun returnsOriginalWhenNoCause() {
        val lone = MockFDBException("solo")

        assertSame(lone, lone.unwrapFdb())
    }
}
