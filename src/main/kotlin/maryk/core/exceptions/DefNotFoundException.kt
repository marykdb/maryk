package maryk.core.exceptions

/** Thrown when definition is not found */
class DefNotFoundException : Throwable {
    /** @param e cause for this exception */
    constructor(e: Throwable) : super(e) {}

    /** @param message to be set for exception explaining cause */
    constructor(message: String) : super(message) {}
}