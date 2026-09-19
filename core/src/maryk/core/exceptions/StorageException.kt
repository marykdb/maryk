package maryk.core.exceptions

/** Exception with [message] for when something is unexpected in storage */
class StorageException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
