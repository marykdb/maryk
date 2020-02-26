package maryk.datastore.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel

class UpdateProcessor : CoroutineScope {
    private val updateJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + updateJob

    val updateListeners = mutableListOf<SendChannel<Update>>()

    val updateSendChannel = processUpdateActor()

    fun close() {
        updateJob.cancel()

        updateSendChannel.close()

        updateListeners.forEach {
            it.close()
        }
    }
}
