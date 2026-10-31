package maryk.datastore.memory

import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import maryk.datastore.test.assertAllocationErrorsRemainSerializable
import maryk.datastore.test.assertAllocationReplayCreatesMissingMaps
import maryk.datastore.test.assertAnonymousCollectionMutations
import maryk.datastore.test.assertCollectionTargetsRespectListBounds
import maryk.datastore.test.assertDeletedCollectionConstraints
import maryk.datastore.test.assertDecimalWriteIntegrity
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
    @Test
    fun unrepresentableStringsCannotBecomeStoredData() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels)
        try {
            assertUnicodeWriteIntegrity(store)
        } finally { store.close() }
    }

    @Test
    fun malformedMapKeyRejectionsRemainSerializable() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels)
        try {
            assertMalformedKeyDiagnosticsCanTravel(store)
        } finally { store.close() }
    }

    @Test
    fun typedValuesRespectDefinitions() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertTypedValuesRespectDefinitions(store) } finally { store.close() }
        }
    }

    @Test
    fun immutableValuesAndKeysCannotChange() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertImmutableWriteIntegrity(store) } finally { store.close() }
        }
    }

    @Test
    fun indexBoundsHandleShortRows() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels)
        try { assertIndexBoundsHandleShortRows(store) } finally { store.close() }
    }
    @Test
    fun allocationErrorsRemainSerializable() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertAllocationErrorsRemainSerializable(store) } finally { store.close() }
        }
    }

    @Test
    fun anonymousCollectionDeltasPreserveValues() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertAnonymousCollectionMutations(store) } finally { store.close() }
        }
    }
    @Test
    fun resolvedAllocationReplayCreatesMissingMaps() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertAllocationReplayCreatesMissingMaps(store) } finally { store.close() }
        }
    }
    @Test
    fun collectionTargetsRespectListBounds() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertCollectionTargetsRespectListBounds(store) } finally { store.close() }
        }
    }
    @Test
    fun deletedRowsRetainCollectionConstraints() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertDeletedCollectionConstraints(store) } finally { store.close() }
        }
    }
    @Test
    fun simpleTypedReferencesCannotOverwriteDiscriminators() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertSimpleTypedReferenceWriteIntegrity(store) } finally { store.close() }
        }
    }
    @Test
    fun setItemWritesPreserveQualifierIdentity() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertSetItemWriteIntegrity(store) } finally { store.close() }
        }
    }

    @Test
    fun decimalsMustFitTheirScaleAndStorageWidth() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertDecimalWriteIntegrity(store) } finally { store.close() }
        }
    }
    @Test
    fun enumValuesMustBeReadableByTheirDefinition() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = validatedIntegrityModels, keepAllVersions = history)
            try { assertEnumWriteIntegrity(store) } finally { store.close() }
        }
    }
}
