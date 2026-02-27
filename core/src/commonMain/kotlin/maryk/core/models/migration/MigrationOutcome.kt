package maryk.core.models.migration

/**
 * Rich migration outcomes for resumable migration handling.
 */
sealed class MigrationOutcome {
    object Success : MigrationOutcome()

    data class Partial(
        val nextCursor: ByteArray? = null,
        val message: String? = null,
    ) : MigrationOutcome()

    data class Retry(
        val nextCursor: ByteArray? = null,
        val message: String? = null,
        val retryAfterMs: Long? = null,
    ) : MigrationOutcome()

    data class Fatal(
        val reason: String,
    ) : MigrationOutcome()
}
