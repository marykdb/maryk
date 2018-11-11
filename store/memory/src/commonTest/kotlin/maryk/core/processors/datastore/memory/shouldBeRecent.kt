@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore.memory

import maryk.lib.time.Instant
import maryk.test.shouldBe

fun shouldBeRecent(time: ULong, maxDifference: ULong) {
    val timeSinceInsert = (Instant.getCurrentEpochTimeInMillis().toULong() - time)
    (timeSinceInsert in (0uL .. maxDifference)) shouldBe true
}
