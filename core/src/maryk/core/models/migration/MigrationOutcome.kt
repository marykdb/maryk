package maryk.core.models.migration

/**
 * Rich migration outcomes for resumable migration handling.
 */
sealed class MigrationOutcome {
    object Success : MigrationOutcome()

    data class Partial(
        val nextCursor: ByteArray? = null,
        val message: String? = null,
    ) : MigrationOutcome() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Partial) return false
            if (!nextCursor.contentEquals(other.nextCursor)) return false
            return message == other.message
        }

        override fun hashCode(): Int {
            var result = nextCursor?.contentHashCode() ?: 0
            result = 31 * result + (message?.hashCode() ?: 0)
            return result
        }
    }

    data class Retry(
        val nextCursor: ByteArray? = null,
        val message: String? = null,
        val retryAfterMs: Long? = null,
    ) : MigrationOutcome() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Retry) return false
            if (!nextCursor.contentEquals(other.nextCursor)) return false
            if (message != other.message) return false
            return retryAfterMs == other.retryAfterMs
        }

        override fun hashCode(): Int {
            var result = nextCursor?.contentHashCode() ?: 0
            result = 31 * result + (message?.hashCode() ?: 0)
            result = 31 * result + (retryAfterMs?.hashCode() ?: 0)
            return result
        }
    }

    data class Fatal(
        val reason: String,
    ) : MigrationOutcome()
}
