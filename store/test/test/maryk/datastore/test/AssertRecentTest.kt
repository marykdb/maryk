@file:OptIn(kotlin.time.ExperimentalTime::class)

package maryk.datastore.test

import maryk.core.clock.HLC
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class AssertRecentTest {
    @Test
    fun acceptsTimestampSlightlyInFuture() {
        val now = Clock.System.now().toEpochMilliseconds().toULong()
        val futureTimestamp = HLC(physical = now + 25uL, logical = 0u).timestamp

        assertRecent(futureTimestamp, maxDifference = 50uL)
    }

    @Test
    fun failsForTimestampOutsideThreshold() {
        val now = Clock.System.now().toEpochMilliseconds().toULong()
        val oldTimestamp = HLC(physical = now - 200uL, logical = 0u).timestamp

        assertFailsWith<AssertionError> {
            assertRecent(oldTimestamp, maxDifference = 50uL)
        }
    }
}
