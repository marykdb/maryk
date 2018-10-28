package maryk.lib.time

/** Defines the current Instant time */
expect object Instant {
    /** get the current epoch time in milliseconds since 1-1-1970 */
    fun getCurrentEpochTimeInMillis(): Long
}
