package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.async.AsyncIterator

/**
 * Synchronously obtain the next element from an [AsyncIterator] for code paths that are still
 * structured around blocking iteration.
 */
internal fun <T> AsyncIterator<T>.nextBlocking(): T = runBlocking { next() }
