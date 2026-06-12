package maryk.datastore.shared

import kotlinx.coroutines.CancellationException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.exceptions.ValidationException
import maryk.lib.exceptions.ParseException

/** Rethrow failures which should not be converted to datastore responses. */
fun Throwable.rethrowIfFatal() {
    if (this is CancellationException || (this is Error && this !is DefNotFoundException && this !is ValidationException && this !is ParseException)) {
        throw this
    }
}

inline fun <T> runCatchingNonFatal(block: () -> T): Result<T> =
    runCatching(block).onFailure { it.rethrowIfFatal() }
