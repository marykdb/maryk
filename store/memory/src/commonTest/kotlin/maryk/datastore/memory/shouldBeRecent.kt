package maryk.datastore.memory

import maryk.lib.time.Instant
import kotlin.test.assertTrue

fun shouldBeRecent(time: ULong, maxDifference: ULong) {
    val timeSinceInsert = (Instant.getCurrentEpochTimeInMillis().toULong() - time)
    assertTrue { timeSinceInsert in (0uL..maxDifference) }
}
