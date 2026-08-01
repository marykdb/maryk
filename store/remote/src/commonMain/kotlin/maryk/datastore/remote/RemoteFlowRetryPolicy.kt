package maryk.datastore.remote

/** Reconnect policy for unexpectedly interrupted remote update flows. */
data class RemoteFlowRetryPolicy(
    val maxReconnectAttempts: UInt = 0u,
    val initialDelayMillis: Long = 250,
    val maxDelayMillis: Long = 5_000,
    val backoffMultiplier: Double = 2.0,
    val heartbeatTimeoutMillis: Long? = null,
) {
    init {
        require(initialDelayMillis >= 0) { "Initial reconnect delay cannot be negative" }
        require(maxDelayMillis >= initialDelayMillis) {
            "Maximum reconnect delay cannot be smaller than the initial delay"
        }
        require(backoffMultiplier >= 1.0) { "Reconnect backoff multiplier must be at least 1" }
        require(heartbeatTimeoutMillis == null || heartbeatTimeoutMillis > 0) {
            "Heartbeat timeout must be positive"
        }
    }

    companion object {
        val Disabled = RemoteFlowRetryPolicy()
        val Default = RemoteFlowRetryPolicy(maxReconnectAttempts = 5u)
    }
}

/** A remote flow ended without a protocol-level completion signal. */
class RemoteFlowDisconnectedException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
