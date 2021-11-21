package maryk.datastore.test

import kotlinx.datetime.Clock
import maryk.core.clock.HLC
import kotlin.test.assertTrue

fun assertRecent(time: ULong, maxDifference: ULong) {
    val timeSinceInsert = Clock.System.now().toEpochMilliseconds().toULong() - HLC(time).toPhysicalUnixTime()
    assertTrue("Time to be recent: $timeSinceInsert within $maxDifference") { timeSinceInsert in (0uL..maxDifference) }
}
