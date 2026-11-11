package maryk.datastore.foundationdb

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Restore attempts release current unique claims before applying their mutations. */
internal class FoundationDBRestoreContext(val owner: FoundationDBDataStore) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<FoundationDBRestoreContext>
}
