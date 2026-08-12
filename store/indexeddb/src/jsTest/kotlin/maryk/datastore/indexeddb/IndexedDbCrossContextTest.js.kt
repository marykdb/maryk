package maryk.datastore.indexeddb

import kotlin.test.Test

class IndexedDbCrossContextTest {
    @Test
    fun versionsAreGlobal() = twoInstancesAllocateStrictlyIncreasingVersions()

    @Test
    fun flowsObserveOtherContexts() = flowObservesDurableCrossContextAddChangeDeleteOnce()

    @Test
    fun orderedFlowsObserveIndexChanges() = orderedFlowReplaysCrossContextIndexChange()

    @Test
    fun pollingWorksWithoutBroadcastChannel() = pollingDeliversWithoutBroadcastChannel()

    @Test
    fun localCommitDrainsPendingExternalCommit() = localCommitCannotSkipPendingExternalCommit()

    @Test
    fun checkOnlyChangeSucceeds() = checkOnlyChangeRemainsSuccessful()

    @Test
    fun reopenUsesJournalFloor() = reopenStartsAtPersistedJournalFloor()

    @Test
    fun retentionGapRequiresFreshSnapshot() = retentionGapFailsListenerExplicitly()

    @Test
    fun lockModesCoordinate() = webLockAndFallbackLeaseShareOneFence()
}
