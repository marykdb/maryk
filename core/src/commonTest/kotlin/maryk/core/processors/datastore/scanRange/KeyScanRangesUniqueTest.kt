package maryk.core.processors.datastore.scanRange

import maryk.core.properties.definitions.StringDefinition
import maryk.core.query.filters.Equals
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.lib.extensions.toHex
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

class KeyScanRangesUniqueTest {
    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            CompleteMarykModel { string::ref } with "🦄"
        )

        val scanRange = CompleteMarykModel.createScanRange(filter, null)

        expect(1) { scanRange.uniques?.size }
        scanRange.uniques?.get(0)?.let {
            expect("09") { it.reference.toHex() }
            expect(CompleteMarykModel.string.definition) {
                it.definition as StringDefinition
            }
            expect("🦄") { it.value }

            true
        } ?: fail("Should be defined")
    }

    @Test
    fun convertValueInFilterToScanRange() {
        val filter = ValueIn(
            CompleteMarykModel { string::ref } with setOf("🦄", "💩", "🤡", "🤖")
        )

        val scanRange = CompleteMarykModel.createScanRange(filter, null)

        expect(4) { scanRange.uniques?.size }
        scanRange.uniques?.get(0)?.let {
            expect("09") { it.reference.toHex() }
            expect(CompleteMarykModel.string.definition) {
                it.definition as StringDefinition
            }
            expect("🦄") { it.value }

            true
        } ?: fail("Should be defined")

        scanRange.uniques?.get(3)?.let {
            expect("09") { it.reference.toHex() }
            expect(CompleteMarykModel.string.definition) {
                it.definition as StringDefinition
            }
            expect("🤖") { it.value }

            true
        } ?: fail("Should be defined")
    }
}
