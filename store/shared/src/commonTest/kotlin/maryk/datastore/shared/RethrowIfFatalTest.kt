package maryk.datastore.shared

import kotlinx.coroutines.CancellationException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RethrowIfFatalTest {
    @Test
    fun cancellationIsFatal() {
        assertFailsWith<CancellationException> {
            CancellationException("cancelled").rethrowIfFatal()
        }
    }

    @Test
    fun regularErrorIsFatal() {
        assertFailsWith<Error> {
            Error("fatal").rethrowIfFatal()
        }
    }

    @Test
    fun parseExceptionIsNotFatal() {
        ParseException("bad input").rethrowIfFatal()
    }

    @Test
    fun validationExceptionIsNotFatal() {
        InvalidValueException(null, "bad input").rethrowIfFatal()
    }

    @Test
    fun definitionNotFoundExceptionIsNotFatal() {
        DefNotFoundException("missing").rethrowIfFatal()
    }

    @Test
    fun runCatchingNonFatalReturnsSuccess() {
        assertEquals("ok", runCatchingNonFatal { "ok" }.getOrThrow())
    }

    @Test
    fun runCatchingNonFatalCapturesNonFatal() {
        val result = runCatchingNonFatal {
            throw ParseException("bad input")
        }

        assertTrue(result.isFailure)
        assertIs<ParseException>(result.exceptionOrNull())
    }

    @Test
    fun runCatchingNonFatalRethrowsFatal() {
        assertFailsWith<CancellationException> {
            runCatchingNonFatal {
                throw CancellationException("cancelled")
            }
        }
    }
}
