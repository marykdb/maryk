package maryk.datastore.foundationdb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.assertAllocationErrorsRemainSerializable
import maryk.datastore.test.assertAllocationReplayCreatesMissingMaps
import maryk.datastore.test.assertAnonymousCollectionMutations
import maryk.datastore.test.assertCollectionTargetsRespectListBounds
import maryk.datastore.test.assertDeletedCollectionConstraints
import maryk.datastore.test.assertDecimalWriteIntegrity
import maryk.datastore.test.assertErasedTypeWriteIntegrity
import maryk.datastore.test.assertStringRangeIntegrity
import maryk.datastore.test.assertEnumWriteIntegrity
import maryk.datastore.test.assertImmutableWriteIntegrity
import maryk.datastore.test.assertIndexBoundsHandleShortRows
import maryk.datastore.test.assertMalformedKeyDiagnosticsCanTravel
import maryk.datastore.test.assertSetItemWriteIntegrity
import maryk.datastore.test.assertSimpleTypedReferenceWriteIntegrity
import maryk.datastore.test.assertTypedValuesRespectDefinitions
import maryk.datastore.test.assertUnicodeWriteIntegrity
import maryk.datastore.test.validatedIntegrityModels

class ValueIntegrityTest {
    private suspend fun withStore(history: Boolean = false, block: suspend (IsDataStore) -> Unit) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "value-integrity-${Random.nextInt()}"),
            dataModelsById = validatedIntegrityModels, keepAllVersions = history,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun unrepresentableStringsCannotBecomeStoredData() = runTest(timeout = 3.minutes) {
        withStore { assertUnicodeWriteIntegrity(it) }
    }

    @Test
    fun malformedMapKeyRejectionsRemainSerializable() = runTest(timeout = 3.minutes) {
        withStore { assertMalformedKeyDiagnosticsCanTravel(it) }
    }

    @Test
    fun typedValuesRespectDefinitions() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertTypedValuesRespectDefinitions(it) }
    }

    @Test
    fun immutableValuesAndKeysCannotChange() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertImmutableWriteIntegrity(it) }
    }

    @Test
    fun indexBoundsHandleShortRows() = runTest(timeout = 3.minutes) {
        withStore { assertIndexBoundsHandleShortRows(it) }
    }
    @Test
    fun allocationErrorsRemainSerializable() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertAllocationErrorsRemainSerializable(it) }
    }

    @Test
    fun anonymousCollectionDeltasPreserveValues() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertAnonymousCollectionMutations(it) }
    }
    @Test
    fun resolvedAllocationReplayCreatesMissingMaps() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertAllocationReplayCreatesMissingMaps(it) }
    }
    @Test
    fun collectionTargetsRespectListBounds() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertCollectionTargetsRespectListBounds(it) }
    }
    @Test
    fun deletedRowsRetainCollectionConstraints() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertDeletedCollectionConstraints(it) }
    }
    @Test
    fun simpleTypedReferencesCannotOverwriteDiscriminators() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertSimpleTypedReferenceWriteIntegrity(it) }
    }
    @Test
    fun setItemWritesPreserveQualifierIdentity() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertSetItemWriteIntegrity(it) }
    }

    @Test
    fun decimalsMustFitTheirScaleAndStorageWidth() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertDecimalWriteIntegrity(it) }
    }
    @Test
    fun enumValuesMustBeReadableByTheirDefinition() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertEnumWriteIntegrity(it) }
    }
    @Test
    fun erasedWritesCannotBypassTheirDeclaredTypes() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertErasedTypeWriteIntegrity(it) }
    }
    @Test
    fun indexedStringRangesMatchTableScans() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history) { assertStringRangeIntegrity(it) }
    }
}
