package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.model.readStoredModelDefinitionsFromPath
import maryk.datastore.rocksdb.model.modelDefinitionKey
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object ShortSourceReferenceTarget : RootDataModel<ShortSourceReferenceTarget>(
    name = "SourceReferenceTarget",
    keyDefinition = { ShortSourceReferenceTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}

private object LongSourceReferenceTarget : RootDataModel<LongSourceReferenceTarget>(
    name = "SourceReferenceTarget",
    keyDefinition = { LongSourceReferenceTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt64, final = true)
}

private class SourceReferenceOwner(target: () -> IsRootDataModel) : RootDataModel<SourceReferenceOwner>(
    name = "SourceReferenceOwner",
) {
    val target by reference(index = 1u, dataModel = target)
}

class SourceReferenceWidthTest {
    @Test
    fun newMetadataRetainsWidthsForUnregisteredReferenceTargets() = runTest {
        val path = createTestDBFolder("externalSourceReferenceWidth")
        val owner = SourceReferenceOwner { ShortSourceReferenceTarget }
        try {
            val added = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to owner)).let { store ->
                try {
                    assertIs<AddSuccess<SourceReferenceOwner>>(store.execute(owner.add(owner.create {
                        target with ShortSourceReferenceTarget.key(byteArrayOf(1, 2, 3, 4))
                    })).statuses.single())
                } finally { store.close() }
            }
            val source = readStoredModelDefinitionsFromPath(path).getValue(1u)
            assertEquals(4, (source[1u]!!.definition as IsReferenceDefinition<*, *>).byteSize)
            assertIs<NeedsMigration>(SourceReferenceOwner { LongSourceReferenceTarget }.isMigrationNeeded(source))
            val reopened = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to owner))
            try {
                assertEquals(added.version, reopened.execute(owner.get(added.key)).values.single().lastVersion)
            } finally { reopened.close() }
        } finally { deleteFolder(path) }
    }

    @Test
    fun oldExternalReferencesWithoutWidthsRequireAuthoritativeSourceSchemas() = runTest {
        val path = createTestDBFolder("unknownExternalSourceReferenceWidth")
        val owner = SourceReferenceOwner { ShortSourceReferenceTarget }
        try {
            val store = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to owner))
            try {
                val context = DefinitionsConversionContext()
                val cache = WriteCache()
                val length = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(owner, cache, context)
                val bytes = ByteArray(length)
                var offset = 0
                RootDataModel.Model.Serializer.writeObjectProtoBuf(owner, cache, { bytes[offset++] = it }, context)
                store.db.put(store.getColumnFamilies(1u).model, modelDefinitionKey, bytes)
            } finally { store.close() }
            val failure = assertFailsWith<MigrationException> {
                RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to owner))
            }
            assertTrue(failure.message.orEmpty().contains("authoritative source schema"))
        } finally { deleteFolder(path) }
    }

    @Test
    fun persistedSourceWidthCannotBeReplacedByConfiguredTargetWidth() = runTest {
        val path = createTestDBFolder("sourceReferenceWidth")
        val owner = SourceReferenceOwner { ShortSourceReferenceTarget }
        val currentOwner = SourceReferenceOwner { LongSourceReferenceTarget }
        val sourceModels = mapOf(1u to owner, 2u to ShortSourceReferenceTarget)
        try {
            val added = RocksDBDataStore.open(relativePath = path, dataModelsById = sourceModels, keepAllVersions = true).let { store ->
                try {
                    val target = assertIs<AddSuccess<ShortSourceReferenceTarget>>(store.execute(
                        ShortSourceReferenceTarget.add(ShortSourceReferenceTarget.create { id with 1u })
                    ).statuses.single())
                    assertIs<AddSuccess<SourceReferenceOwner>>(store.execute(owner.add(owner.create {
                        this.target with target.key
                    })).statuses.single())
                } finally { store.close() }
            }

            val stored = readStoredModelDefinitionsFromPath(path).getValue(1u)
            assertEquals(4, (stored[1u]!!.definition as IsReferenceDefinition<*, *>).byteSize)
            assertIs<NeedsMigration>(currentOwner.isMigrationNeeded(stored))
            assertFailsWith<MigrationException> {
                RocksDBDataStore.open(relativePath = path, keepAllVersions = true,
                    dataModelsById = mapOf(1u to currentOwner, 2u to LongSourceReferenceTarget))
            }

            val reopened = RocksDBDataStore.open(relativePath = path, dataModelsById = sourceModels, keepAllVersions = true)
            try {
                val current = reopened.execute(owner.get(added.key)).values.single()
                assertEquals(added.version, current.lastVersion)
                assertEquals(4, current.values { target }!!.size)
            } finally { reopened.close() }
        } finally { deleteFolder(path) }
    }
}
