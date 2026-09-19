package maryk.datastore.shared.updates

/** Failure isolated to one datastore update listener. */
sealed class UpdateListenerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** The consumer did not drain updates quickly enough to preserve a complete ordered stream. */
class UpdateListenerOverflowException(modelName: String) : UpdateListenerException(
    "Update listener for $modelName could not keep up; resubscribe for a fresh snapshot"
)

/** Processing an update failed for one listener. */
class UpdateListenerProcessingException(modelName: String, cause: Throwable) : UpdateListenerException(
    "Update listener for $modelName failed",
    cause,
)
