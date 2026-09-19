package maryk.datastore.shared.updates

import kotlinx.atomicfu.atomic

/** Bounded update buffer used while a flow's initial response is read. */
internal class PendingUpdateListener {
    private val updates = ArrayDeque<FlowUpdate>()
    private var failure: Throwable? = null
    private var snapshotBoundary: FlowSnapshotBoundary? = null
    private var cancelled = false

    val activatedListener = atomic<UpdateListener<*, *>?>(null)

    fun add(update: FlowUpdate): Boolean {
        if (snapshotBoundary?.let(update::isIncludedIn) == true) return true
        if (failure != null) return false
        if (updates.size == UPDATE_LISTENER_MAILBOX_CAPACITY) {
            failure = UpdateListenerOverflowException(update.update.dataModel.Meta.name)
            return false
        }
        updates.addLast(update)
        return true
    }

    fun setSnapshotBoundary(boundary: FlowSnapshotBoundary) {
        snapshotBoundary = boundary
        updates.removeAll { it.isIncludedIn(boundary) }
    }

    fun takeUpdates(): List<FlowUpdate> {
        failure?.let { throw it }
        return buildList(updates.size) {
            while (updates.isNotEmpty()) {
                add(updates.removeFirst())
            }
        }
    }

    fun activate(listener: UpdateListener<*, *>) {
        activatedListener.value = listener
    }

    fun cancel() {
        cancelled = true
        updates.clear()
    }

    fun isCancelled() = cancelled
}

internal const val UPDATE_LISTENER_MAILBOX_CAPACITY = 64
