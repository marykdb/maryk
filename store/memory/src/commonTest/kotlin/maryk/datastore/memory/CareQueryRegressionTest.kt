package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.CareQueryRegressionCases
import maryk.datastore.test.careQueryRegressionModels
import kotlin.test.Test

class CareQueryRegressionTest {
    @Test
    fun equalityPrefixWithResidualMembershipKeepsMatchingRows() = runTest {
        cases.equalityPrefixWithResidualMembershipKeepsMatchingRows()
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
    fun compositeEqualityListenerAcceptsMatchingAddition() = runTest {
        cases.compositeEqualityListenerAcceptsMatchingAddition()
    }

    private val cases = CareQueryRegressionCases {
        InMemoryDataStore.open(keepAllVersions = true, keepUpdateHistoryIndex = true, dataModelsById = careQueryRegressionModels)
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
    fun compositeEqualityWithNonLeadingEnum() = runTest { cases.compositeEqualityWithNonLeadingEnum() }

    @Test
    fun compositeEqualityWithTwoEnums() = runTest { cases.compositeEqualityWithTwoEnums() }













}
