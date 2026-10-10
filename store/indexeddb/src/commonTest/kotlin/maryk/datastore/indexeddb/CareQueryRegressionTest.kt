package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.CareQueryRegressionCases
import maryk.datastore.test.careQueryRegressionModels
import kotlin.test.Test
import kotlin.random.Random

class CareQueryRegressionTest {
    @Test
    fun equalityPrefixWithResidualMembershipKeepsMatchingRows() = runTest {
        cases.equalityPrefixWithResidualMembershipKeepsMatchingRows()
    }

    private val cases = CareQueryRegressionCases { name ->
        installIndexedDbForTests()
        IndexedDbDataStore.open(
            databaseName = "$name-${Random.nextInt()}",
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = careQueryRegressionModels,
        )
    }

    @Test
    fun compositeMembershipCrossProductKeepsOrderAndContinuation() = runTest {
        cases.compositeMembershipCrossProductKeepsOrderAndContinuation()
    }

    @Test
    fun membershipContinuationTraversesDisjointRanges() = runTest {
        cases.membershipContinuationTraversesDisjointRanges()
    }

    @Test
    fun changesIncludeCreationAtExactLowerBound() = runTest {
        cases.changesIncludeCreationAtExactLowerBound()
    }

    @Test
    fun updatesIncludeAdditionAtExactLowerBound() = runTest {
        cases.updatesIncludeAdditionAtExactLowerBound()
    }

    @Test
    fun emptyCompositeMembershipReturnsNoRows() = runTest {
        cases.emptyCompositeMembershipReturnsNoRows()
    }

    @Test
    fun descendingMembershipListenerAcceptsMatchingAddition() = runTest {
        cases.descendingMembershipListenerAcceptsMatchingAddition()
    }

    @Test
    fun compositeMembershipKeepsMatchingRecords() = runTest {
        cases.compositeMembershipKeepsMatchingRecords()
    }

    @Test
    fun updateHistoryKeepsAdjacentVersionsDistinct() = runTest {
        cases.updateHistoryKeepsAdjacentVersionsDistinct()
    }

    @Test
    fun updateHistoryReturnsCreationsAsAdditions() = runTest {
        cases.updateHistoryReturnsCreationsAsAdditions()
    }

    @Test
    fun updateHistoryKeepsHardDeleteAfterKeyReuse() = runTest {
        cases.updateHistoryKeepsHardDeleteAfterKeyReuse()
    }

    @Test
    fun ascendingTableContinuationExhaustsAtMaximumKey() = runTest {
        cases.ascendingTableContinuationExhaustsAtMaximumKey()
    }

    @Test
    fun descendingTableContinuationExhaustsAtMinimumKey() = runTest {
        cases.descendingTableContinuationExhaustsAtMinimumKey()
    }

    @Test
    fun scopedChangeScanKeepsDescendingRangeAndContinuation() = runTest {
        cases.scopedChangeScanKeepsDescendingRangeAndContinuation()
    }

    @Test
    fun ascendingContinuationAcrossByteCarryKeepsAdjacentRecords() = runTest {
        cases.ascendingContinuationAcrossByteCarryKeepsAdjacentRecords()
    }

    @Test
    fun descendingContinuationKeepsAdjacentRecords() = runTest { cases.descendingContinuationKeepsAdjacentRecords() }

    @Test
    fun descendingMaximumBytePrefixAdvances() = runTest { cases.descendingMaximumBytePrefixAdvances() }

    @Test
    fun compositeEqualityWithNonLeadingEnum() = runTest { cases.compositeEqualityWithNonLeadingEnum() }

    @Test
    fun compositeEqualityWithTwoEnums() = runTest { cases.compositeEqualityWithTwoEnums() }

    @Test
    fun descendingStatusWithResidualEnumTerminates() = runTest { cases.descendingStatusWithResidualEnumTerminates() }

    @Test
    fun descendingStatusCursorReachesOlderRecordWithoutRepeatingPages() = runTest { cases.descendingStatusCursorReachesOlderRecordWithoutRepeatingPages() }

    @Test
    fun descendingStatusRootKeyReachesOlderRecordWithoutRepeatingPages() = runTest { cases.descendingStatusRootKeyReachesOlderRecordWithoutRepeatingPages() }

    @Test
    fun descendingParticipantCursorTraversesMoreThanFiveHundredRecords() = runTest { cases.descendingParticipantCursorTraversesMoreThanFiveHundredRecords() }

    @Test
    fun descendingActorDateRangesTerminateWithoutContinuation() = runTest { cases.descendingActorDateRangesTerminateWithoutContinuation() }

    @Test
    fun historicalNameScanKeepsDeletedOptionalEmbedAbsent() = runTest { cases.historicalNameScanKeepsDeletedOptionalEmbedAbsent() }

}
