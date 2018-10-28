package maryk.core.exceptions

/** Exception thrown when no context was found */
class ContextNotFoundException : Throwable("No context was passed or value was set to retrieve context value")
