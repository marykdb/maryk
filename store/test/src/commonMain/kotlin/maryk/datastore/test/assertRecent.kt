@file:OptIn(ExperimentalTime::class)

package maryk.datastore.test

import maryk.core.clock.HLC
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun assertRecent(time: ULong, maxDifference: ULong) {
    val timeSinceInsert = Clock.System.now().toEpochMilliseconds().toULong() - HLC(time).toPhysicalUnixTime()
    assertTrue("Time to be recent: $timeSinceInsert within $maxDifference") { timeSinceInsert in (0uL..maxDifference) }
}
