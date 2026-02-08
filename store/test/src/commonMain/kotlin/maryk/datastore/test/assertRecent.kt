@file:OptIn(ExperimentalTime::class)

package maryk.datastore.test

import maryk.core.clock.HLC
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun assertRecent(time: ULong, maxDifference: ULong) {
    val now = Clock.System.now().toEpochMilliseconds()
    val observed = HLC(time).toPhysicalUnixTime().toLong()
    val delta = now - observed
    val absoluteDelta = if (delta >= 0) delta.toULong() else (-delta).toULong()
    assertTrue("Time to be recent: delta=${delta}ms within ±$maxDifference") {
        absoluteDelta <= maxDifference
    }
}
