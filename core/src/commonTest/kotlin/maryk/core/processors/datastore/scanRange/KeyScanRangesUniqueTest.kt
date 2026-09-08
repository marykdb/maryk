package maryk.core.processors.datastore.scanRange

import maryk.core.properties.definitions.StringDefinition
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

class KeyScanRangesUniqueTest {
    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            CompleteMarykModel.ref { string } with "🦄"
        )

        val scanRange = CompleteMarykModel.createScanRange(filter, null)

        expect(1) { scanRange.uniques?.size }
        scanRange.uniques?.get(0)?.let {
            expect("09") { it.reference.toHexString() }
            expect(CompleteMarykModel.string.definition) {
                it.definition as StringDefinition
            }
            expect("🦄") { it.value }

            true
        } ?: fail("Should be defined")
    }

    @Test
    fun multipleUniqueAlternativesDoNotBecomeSingleRecordLookups() {
        val filter = ValueIn(
            CompleteMarykModel.ref { string } with setOf("🦄", "💩", "🤡", "🤖")
        )

        val scanRange = CompleteMarykModel.createScanRange(filter, null)

        val uniques = scanRange.uniques ?: fail("Should be defined")

        expect(0) { uniques.size }
    }

    @Test
    fun singleUniqueAlternativeRetainsUniqueLookup() {
        val scanRange = CompleteMarykModel.createScanRange(
            ValueIn(CompleteMarykModel.ref { string } with setOf("🦄")), null
        )
        val uniques = scanRange.uniques ?: fail("Should be defined")
        expect(1) { uniques.size }
        uniques.single().let {
            expect("09") { it.reference.toHexString() }
            expect(CompleteMarykModel.string.definition) {
                it.definition as StringDefinition
            }
            expect("🦄") { it.value }

            true
        }

    }

    @Test
    fun uniquePrefixDoesNotBecomeExactLookup() {
        val scanRange = CompleteMarykModel.createScanRange(
            Prefix(CompleteMarykModel.ref { string } with "prefix"), null
        )
        expect(0) { scanRange.uniques?.size }
    }
}
