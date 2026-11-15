package maryk.datastore.shared

/** A lazy native read snapshot that must be captured before flow/backup callbacks run. */
interface DeferredReadSnapshot {
    fun captureSnapshot()
}
