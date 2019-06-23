package maryk.datastore.memory

import maryk.core.clock.HLC
import maryk.lib.time.Instant
import kotlin.test.assertTrue

fun assertRecent(time: ULong, maxDifference: ULong) {
    val timeSinceInsert = (Instant.getCurrentEpochTimeInMillis().toULong() - HLC(time).toPhysicalUnixTime())
    assertTrue("Time to be recent: $timeSinceInsert within $maxDifference") { timeSinceInsert in (0uL..maxDifference) }
}
