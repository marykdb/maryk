package maryk.datastore.foundationdb

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Bytes
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** An export may span native MVCC windows only while its datastore revision stays unchanged. */
internal class FoundationDBStateBackupContext(val revision: Bytes) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<FoundationDBStateBackupContext>
}

class FoundationDBStateBackupChangedException : RequestException(
    "FoundationDB changed during state backup; discard the incomplete archive and retry while writes are quiescent",
)
