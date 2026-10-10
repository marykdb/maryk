package maryk.datastore.foundationdb

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
        FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", name, Random.nextInt().toString()),
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
