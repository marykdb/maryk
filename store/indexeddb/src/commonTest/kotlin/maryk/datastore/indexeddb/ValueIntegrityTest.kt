package maryk.datastore.indexeddb

import kotlin.random.Random
import kotlin.test.Test
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
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(
            "value-integrity-${Random.nextInt()}", validatedIntegrityModels, keepAllVersions = history,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun unrepresentableStringsCannotBecomeStoredData() = runTest {
        withStore { assertUnicodeWriteIntegrity(it) }
    }

    @Test
    fun malformedMapKeyRejectionsRemainSerializable() = runTest {
        withStore { assertMalformedKeyDiagnosticsCanTravel(it) }
    }

    @Test
    fun typedValuesRespectDefinitions() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertTypedValuesRespectDefinitions(it) }
    }

    @Test
    fun immutableValuesAndKeysCannotChange() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertImmutableWriteIntegrity(it) }
    }

    @Test
    fun indexBoundsHandleShortRows() = runTest {
        withStore { assertIndexBoundsHandleShortRows(it) }
    }
    @Test
    fun allocationErrorsRemainSerializable() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertAllocationErrorsRemainSerializable(it) }
    }

    @Test
    fun anonymousCollectionDeltasPreserveValues() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertAnonymousCollectionMutations(it) }
    }
    @Test
    fun resolvedAllocationReplayCreatesMissingMaps() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertAllocationReplayCreatesMissingMaps(it) }
    }
    @Test
    fun collectionTargetsRespectListBounds() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertCollectionTargetsRespectListBounds(it) }
    }
    @Test
    fun deletedRowsRetainCollectionConstraints() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertDeletedCollectionConstraints(it) }
    }
    @Test
    fun simpleTypedReferencesCannotOverwriteDiscriminators() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertSimpleTypedReferenceWriteIntegrity(it) }
    }
    @Test
    fun setItemWritesPreserveQualifierIdentity() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertSetItemWriteIntegrity(it) }
    }

    @Test
    fun decimalsMustFitTheirScaleAndStorageWidth() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertDecimalWriteIntegrity(it) }
    }
    @Test
    fun enumValuesMustBeReadableByTheirDefinition() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertEnumWriteIntegrity(it) }
    }
    @Test
    fun erasedWritesCannotBypassTheirDeclaredTypes() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertErasedTypeWriteIntegrity(it) }
    }
    @Test
    fun indexedStringRangesMatchTableScans() = runTest {
        for (history in listOf(false, true)) withStore(history) { assertStringRangeIntegrity(it) }
    }
}
