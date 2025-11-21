package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.FdbFuture

/**
 * Bridge the Maryk [FdbFuture] into a blocking wait for existing call sites that are not yet
 * suspending. This keeps behaviour identical to the previous Java client usages.
 */
internal fun <T> FdbFuture<T>.awaitResult(): T = runBlocking { await() }
