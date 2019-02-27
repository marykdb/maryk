package maryk.core.exceptions

/**
 * Exception when something was unexpected with Typing.
 * Probably a wrong cast or a new not completely implemented type
 */
class TypeException(message: String) : Exception(message)
