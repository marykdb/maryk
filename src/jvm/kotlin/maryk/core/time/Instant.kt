package maryk.core.time

object Instant {
    fun getCurrentEpochTimeInMillis() = java.time.Instant.now().toEpochMilli()
}