package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.metadata.LEGACY_INDEX_KEY_FORMAT_VERSION
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.datastore.rocksdb.metadata.StoreMeta
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.rocksdb.metadata.writeStoreMetaFile
import maryk.datastore.test.UniqueModel
import maryk.deleteFolder
import maryk.file.File
import maryk.test.models.AnyValueSetIndexModel
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyIndexFormatGuardTest {
    @Test
    fun reopeningLegacyIndexedStoreMigratesIndexFormat() = runTest {
        val folder = createTestDBFolder("legacy-index-format-guard")

        try {
            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            ).let { store ->
                try {
                    store.execute(
                        AnyValueSetIndexModel.add(
                            AnyValueSetIndexModel.create {
                                name with "legacy"
                                setValues with setOf("s1")
                            }
                        )
                    )
                } finally {
                    store.close()
                }
            }

            writeStoreMetaFile(
                folder,
                StoreMeta(
                    models = readMetaFile(folder),
                    indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION
                )
            )

            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            ).let { store ->
                try {
                    assertEquals(2, readStoreMetaFile(folder).indexKeyFormatVersion)
                    val response = store.execute(
                        AnyValueSetIndexModel.scan(
                            where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "s1")
                        )
                    )
                    assertEquals(listOf("legacy"), response.values.map { it.values { name } })
                } finally {
                    store.close()
                }
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun emptyLegacyStoreMetaIsUpgradedOnOpen() = runTest {
        val folder = createTestDBFolder("legacy-index-format-empty")

        try {
            writeStoreMetaFile(
                folder,
                StoreMeta(
                    models = mapOf(1u to ModelMeta(AnyValueSetIndexModel.Meta.name, AnyValueSetIndexModel.Meta.keyByteSize)),
                    indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION
                )
            )

            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            ).close()

            assertEquals(2, readStoreMetaFile(folder).indexKeyFormatVersion)
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun reopeningIndexedStoreWithoutMetaFileMigratesIndexFormat() = runTest {
        val folder = createTestDBFolder("missing-meta-index-format-guard")

        try {
            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            ).let { store ->
                try {
                    store.execute(
                        AnyValueSetIndexModel.add(
                            AnyValueSetIndexModel.create {
                                name with "missing-meta"
                                setValues with setOf("s2")
                            }
                        )
                    )
                } finally {
                    store.close()
                }
            }

            File.delete("$folder/MARYK_META.yml")

            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            ).let { store ->
                try {
                    assertEquals(2, readStoreMetaFile(folder).indexKeyFormatVersion)
                    val response = store.execute(
                        AnyValueSetIndexModel.scan(
                            where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "s2")
                        )
                    )
                    assertEquals(listOf("missing-meta"), response.values.map { it.values { name } })
                } finally {
                    store.close()
                }
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun reopeningLegacyUniqueOnlyStoreRebuildsUniqueIndex() = runTest {
        val folder = createTestDBFolder("legacy-unique-format-guard")

        try {
            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to UniqueModel),
            ).let { store ->
                try {
                    store.execute(
                        UniqueModel.add(
                            UniqueModel.create {
                                email with "legacy@unique.test"
                            }
                        )
                    )
                } finally {
                    store.close()
                }
            }

            writeStoreMetaFile(
                folder,
                StoreMeta(
                    models = readMetaFile(folder),
                    indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION
                )
            )

            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to UniqueModel),
            ).let { store ->
                try {
                    assertEquals(2, readStoreMetaFile(folder).indexKeyFormatVersion)
                    val response = store.execute(
                        UniqueModel.scan(
                            where = Equals(UniqueModel { email::ref } with "legacy@unique.test")
                        )
                    )
                    assertEquals(listOf("legacy@unique.test"), response.values.map { it.values { email } })
                } finally {
                    store.close()
                }
            }
        } finally {
            deleteFolder(folder)
        }
    }
}
