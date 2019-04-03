package maryk.core.processors.datastore.scanRange

import maryk.core.query.filters.Equals
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.lib.extensions.toHex
import maryk.test.models.CompleteMarykModel
import maryk.test.shouldBe
import kotlin.test.Test
import kotlin.test.fail

class KeyScanRangeUniqueTest {
    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            CompleteMarykModel.ref { string } with "🦄"
        )

        val scanRange = CompleteMarykModel.createScanRange(filter, null)

        scanRange.uniques?.size shouldBe 1
        scanRange.uniques?.get(0)?.let {
            it.reference.toHex() shouldBe "09"
            it.definition shouldBe CompleteMarykModel.Properties.string.definition
            it.value shouldBe "🦄"

            true
        } ?: fail("Should be defined")
    }

    @Test
    fun convertValueInFilterToScanRange() {
        val filter = ValueIn(
            CompleteMarykModel.ref { string } with setOf("🦄", "💩", "🤡", "🤖")
        )

        val scanRange = CompleteMarykModel.createScanRange(filter, null)

        scanRange.uniques?.size shouldBe 4
        scanRange.uniques?.get(0)?.let {
            it.reference.toHex() shouldBe "09"
            it.definition shouldBe CompleteMarykModel.Properties.string.definition
            it.value shouldBe "🦄"

            true
        } ?: fail("Should be defined")

        scanRange.uniques?.get(3)?.let {
            it.reference.toHex() shouldBe "09"
            it.definition shouldBe CompleteMarykModel.Properties.string.definition
            it.value shouldBe "🤖"

            true
        } ?: fail("Should be defined")
    }
}
