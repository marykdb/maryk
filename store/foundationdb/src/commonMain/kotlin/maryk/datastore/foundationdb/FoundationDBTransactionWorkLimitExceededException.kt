package maryk.datastore.foundationdb

import maryk.core.exceptions.RequestException

/** An explicitly configured staged-work budget, distinct from native final commit size. */
class FoundationDBTransactionWorkLimitExceededException(val estimatedBytes: Long, val limitBytes: Long) : RequestException(
    "FoundationDB staged-work estimate $estimatedBytes exceeds the configured $limitBytes-byte work budget",
)
