package maryk.core.time

actual object Instant {
    actual fun getCurrentEpochTimeInMillis() = java.time.Instant.now().toEpochMilli()
}