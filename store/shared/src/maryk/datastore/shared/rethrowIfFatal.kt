package maryk.datastore.shared

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.StorageException
import maryk.core.properties.exceptions.ValidationException
import kotlinx.coroutines.CancellationException
import maryk.lib.exceptions.ParseException

/** Rethrow failures which should not be converted to datastore responses. */
fun Throwable.rethrowIfFatal() {
    if (this is CancellationException || this is Error) {
        throw this
    }
}

/** Returns true for malformed persisted data which callers may skip and continue past. */
fun Throwable.isSkippableDataError() = when (this) {
    is ParseException,
    is StorageException,
    is IllegalArgumentException,
    is IndexOutOfBoundsException,
    is NoSuchElementException -> true
    else -> false
}

inline fun <T> runCatchingNonFatal(block: () -> T): Result<T> =
    runCatching(block).onFailure { it.rethrowIfFatal() }

inline fun <T> recoverNonFatal(block: () -> T, recover: (Exception) -> T): T =
    try {
        block()
    } catch (e: Exception) {
        e.rethrowIfFatal()
        recover(e)
    }
