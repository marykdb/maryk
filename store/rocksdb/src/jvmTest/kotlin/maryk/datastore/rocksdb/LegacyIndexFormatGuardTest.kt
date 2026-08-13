package maryk.datastore.rocksdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.exceptions.StorageException
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.metadata.CURRENT_INDEX_KEY_FORMAT_VERSION
import maryk.datastore.rocksdb.metadata.LEGACY_INDEX_KEY_FORMAT_VERSION
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.datastore.rocksdb.metadata.StoreMeta
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.rocksdb.metadata.writeStoreMetaFile
import maryk.datastore.test.UniqueModel
import maryk.deleteFolder
import maryk.file.File
import maryk.rocksdb.ColumnFamilyHandle
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.ModelV1
import maryk.test.models.ModelV2
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class LegacyIndexFormatGuardTest {
    @Test
    fun incompatibleLegacyOpenPreservesIndexRowsAndMetadata() = runTest {
        val folder = createTestDBFolder("incompatible-legacy-index-format")
        val sentinelKey = byteArrayOf(0x7f)
        val sentinelValue = byteArrayOf(0x45, 0x23)

        try {
            val initialStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to ModelV1),
            )
            initialStore.execute(ModelV1.add(ModelV1.create { value with "haha legacy" }))
            val initialIndex = initialStore.getColumnFamilies(1u).index
            initialStore.db.put(initialIndex, sentinelKey, sentinelValue)
            val rowsBefore = readRows(initialStore, initialIndex)
            initialStore.close()

            val legacyMeta = StoreMeta(
                models = readMetaFile(folder),
                indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION,
            )
            writeStoreMetaFile(folder, legacyMeta)
            val metaBefore = File.readText("$folder/MARYK_META.yml")

            assertFailsWith<MigrationException> {
                RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = true,
                    dataModelsById = mapOf(1u to ModelV2),
                ).close()
            }
            assertEquals(metaBefore, File.readText("$folder/MARYK_META.yml"))

            writeStoreMetaFile(
                folder,
                legacyMeta.copy(indexKeyFormatVersion = CURRENT_INDEX_KEY_FORMAT_VERSION),
            )
            val reopenedStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to ModelV1),
            )
            try {
                val reopenedIndex = reopenedStore.getColumnFamilies(1u).index
                assertEquals(rowsBefore, readRows(reopenedStore, reopenedIndex))
                assertContentEquals(sentinelValue, reopenedStore.db.get(reopenedIndex, sentinelKey))
                assertEquals(
                    listOf("haha legacy"),
                    reopenedStore.execute(
                        ModelV1.scan(where = Equals(ModelV1 { value::ref } with "haha legacy"))
                    ).values.map { it.values { value } },
                )
            } finally {
                reopenedStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun failedSchemaMigrationPreservesLegacyIndexRowsAndMetadata() = runTest {
        val folder = createTestDBFolder("failed-schema-migration-legacy-format")
        val sentinelKey = byteArrayOf(0x7e)
        val sentinelValue = byteArrayOf(0x34, 0x12)

        try {
            val initialStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to ModelV1),
            )
            initialStore.execute(ModelV1.add(ModelV1.create { value with "haha migration" }))
            val initialIndex = initialStore.getColumnFamilies(1u).index
            initialStore.db.put(initialIndex, sentinelKey, sentinelValue)
            val rowsBefore = readRows(initialStore, initialIndex)
            initialStore.close()

            val legacyMeta = StoreMeta(
                models = readMetaFile(folder),
                indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION,
            )
            writeStoreMetaFile(folder, legacyMeta)
            val metaBefore = File.readText("$folder/MARYK_META.yml")

            assertFailsWith<IllegalStateException> {
                RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = true,
                    dataModelsById = mapOf(1u to ModelV2),
                    migrationConfiguration = MigrationConfiguration(
                        migrationHandler = { throw IllegalStateException("migration failed") },
                    ),
                ).close()
            }
            assertEquals(metaBefore, File.readText("$folder/MARYK_META.yml"))

            writeStoreMetaFile(
                folder,
                legacyMeta.copy(indexKeyFormatVersion = CURRENT_INDEX_KEY_FORMAT_VERSION),
            )
            val reopenedStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to ModelV1),
            )
            try {
                val reopenedIndex = reopenedStore.getColumnFamilies(1u).index
                assertEquals(rowsBefore, readRows(reopenedStore, reopenedIndex))
                assertContentEquals(sentinelValue, reopenedStore.db.get(reopenedIndex, sentinelKey))
                assertEquals(
                    listOf("haha migration"),
                    reopenedStore.execute(
                        ModelV1.scan(where = Equals(ModelV1 { value::ref } with "haha migration"))
                    ).values.map { it.values { value } },
                )
            } finally {
                reopenedStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun backgroundSchemaMigrationKeepsLegacyStoreBlockedUntilFormatConversionCompletes() = runTest {
        val folder = createTestDBFolder("background-schema-legacy-format")
        val migrationStarted = CompletableDeferred<Unit>()
        val releaseMigration = CompletableDeferred<Unit>()

        try {
            val initialStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(
                    1u to ModelV1,
                    2u to AnyValueSetIndexModel,
                ),
            )
            initialStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "other model"
                        setValues with setOf("s4")
                    }
                )
            )
            initialStore.close()
            writeStoreMetaFile(
                folder,
                StoreMeta(
                    models = readMetaFile(folder),
                    indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION,
                ),
            )

            val migratingStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(
                    1u to ModelV2,
                    2u to AnyValueSetIndexModel,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationStartupBudgetMs = -1,
                    continueMigrationsInBackground = true,
                    migrationHandler = {
                        migrationStarted.complete(Unit)
                        releaseMigration.await()
                        MigrationOutcome.Success
                    },
                ),
            )
            try {
                migrationStarted.await()
                assertEquals(
                    LEGACY_INDEX_KEY_FORMAT_VERSION,
                    readStoreMetaFile(folder).indexKeyFormatVersion,
                )
                assertFailsWith<MigrationException> {
                    migratingStore.execute(
                        AnyValueSetIndexModel.scan(
                            where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "s4")
                        )
                    )
                }

                releaseMigration.complete(Unit)
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5.seconds) {
                        migratingStore.awaitMigration(1u)
                    }
                }
                assertEquals(
                    CURRENT_INDEX_KEY_FORMAT_VERSION,
                    readStoreMetaFile(folder).indexKeyFormatVersion,
                )
                assertEquals(
                    listOf("other model"),
                    migratingStore.execute(
                        AnyValueSetIndexModel.scan(
                            where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "s4")
                        )
                    ).values.map { it.values { name } },
                )
            } finally {
                releaseMigration.complete(Unit)
                migratingStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun concurrentBackgroundSchemaCompletionsConvertLegacyFormatExactlyOnce() = runTest {
        val folder = createTestDBFolder("concurrent-background-schema-legacy-format")
        val migrationsStarted = Channel<Unit>(capacity = 2)
        val releaseMigrations = CompletableDeferred<Unit>()

        try {
            RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(
                    1u to BackgroundModelA1,
                    2u to BackgroundModelB1,
                    3u to AnyValueSetIndexModel,
                ),
            ).let { store ->
                store.execute(
                    AnyValueSetIndexModel.add(
                        AnyValueSetIndexModel.create {
                            name with "unchanged model"
                            setValues with setOf("s5")
                        }
                    )
                )
                store.close()
            }
            writeStoreMetaFile(
                folder,
                StoreMeta(
                    models = readMetaFile(folder),
                    indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION,
                ),
            )

            val migratingStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(
                    1u to BackgroundModelA2,
                    2u to BackgroundModelB2,
                    3u to AnyValueSetIndexModel,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationStartupBudgetMs = -1,
                    continueMigrationsInBackground = true,
                    migrationHandler = {
                        migrationsStarted.send(Unit)
                        releaseMigrations.await()
                        MigrationOutcome.Success
                    },
                ),
            )
            try {
                migrationsStarted.receive()
                migrationsStarted.receive()
                releaseMigrations.complete(Unit)
                withContext(Dispatchers.Default.limitedParallelism(2)) {
                    withTimeout(5.seconds) {
                        migratingStore.awaitMigration(1u)
                        migratingStore.awaitMigration(2u)
                    }
                }

                assertEquals(
                    CURRENT_INDEX_KEY_FORMAT_VERSION,
                    readStoreMetaFile(folder).indexKeyFormatVersion,
                )
                assertEquals(
                    listOf("unchanged model"),
                    migratingStore.execute(
                        AnyValueSetIndexModel.scan(
                            where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "s5")
                        )
                    ).values.map { it.values { name } },
                )
            } finally {
                releaseMigrations.complete(Unit)
                migratingStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun futureIndexFormatIsRejectedWithoutChangingIndexRowsOrMetadata() = runTest {
        val folder = createTestDBFolder("future-index-format")
        val sentinelKey = byteArrayOf(0x7f)
        val sentinelValue = byteArrayOf(0x67, 0x01)

        try {
            val initialStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            )
            initialStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "future"
                        setValues with setOf("s3")
                    }
                )
            )
            val initialIndex = initialStore.getColumnFamilies(1u).index
            initialStore.db.put(initialIndex, sentinelKey, sentinelValue)
            val rowsBefore = readRows(initialStore, initialIndex)
            initialStore.close()

            val futureMeta = StoreMeta(
                models = readMetaFile(folder),
                indexKeyFormatVersion = CURRENT_INDEX_KEY_FORMAT_VERSION + 1,
            )
            writeStoreMetaFile(folder, futureMeta)
            val metaBefore = File.readText("$folder/MARYK_META.yml")

            assertFailsWith<StorageException> {
                RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = true,
                    dataModelsById = mapOf(1u to AnyValueSetIndexModel),
                ).close()
            }
            assertEquals(metaBefore, File.readText("$folder/MARYK_META.yml"))

            writeStoreMetaFile(
                folder,
                futureMeta.copy(indexKeyFormatVersion = CURRENT_INDEX_KEY_FORMAT_VERSION),
            )
            val reopenedStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            )
            try {
                val reopenedIndex = reopenedStore.getColumnFamilies(1u).index
                assertEquals(rowsBefore, readRows(reopenedStore, reopenedIndex))
                assertContentEquals(sentinelValue, reopenedStore.db.get(reopenedIndex, sentinelKey))
            } finally {
                reopenedStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

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

    private fun readRows(
        store: RocksDBDataStore,
        columnFamily: ColumnFamilyHandle,
    ): List<Pair<List<Byte>, List<Byte>>> =
        buildList {
            store.db.newIterator(columnFamily).use { iterator ->
                iterator.seekToFirst()
                while (iterator.isValid()) {
                    add(iterator.key().toList() to iterator.value().toList())
                    iterator.next()
                }
            }
        }
}

private object BackgroundModelA1 : RootDataModel<BackgroundModelA1>(
    name = "BackgroundModelA",
    version = Version(1),
    indexes = { listOf(BackgroundModelA1.value.ref()) },
) {
    val value by string(index = 1u)
}

private object BackgroundModelA2 : RootDataModel<BackgroundModelA2>(
    name = "BackgroundModelA",
    version = Version(2),
    indexes = { listOf(BackgroundModelA2.value.ref()) },
) {
    val value by string(index = 1u)
    val requiredNumber by number(index = 2u, type = SInt32, required = true)
}

private object BackgroundModelB1 : RootDataModel<BackgroundModelB1>(
    name = "BackgroundModelB",
    version = Version(1),
    indexes = { listOf(BackgroundModelB1.value.ref()) },
) {
    val value by string(index = 1u)
}

private object BackgroundModelB2 : RootDataModel<BackgroundModelB2>(
    name = "BackgroundModelB",
    version = Version(2),
    indexes = { listOf(BackgroundModelB2.value.ref()) },
) {
    val value by string(index = 1u)
    val requiredNumber by number(index = 2u, type = SInt32, required = true)
}
