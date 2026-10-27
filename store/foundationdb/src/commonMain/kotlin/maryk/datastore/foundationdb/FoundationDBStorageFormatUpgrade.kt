package maryk.datastore.foundationdb

import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.requiresStorageFormatUpgrade
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.model.FoundationDBMigrationLease
import maryk.datastore.foundationdb.model.FoundationDBSchemaFence
import maryk.datastore.foundationdb.model.FoundationDBSchemaState
import maryk.datastore.foundationdb.model.beginModelSchemaRebuild
import maryk.datastore.foundationdb.model.modelMigrationStateKey
import maryk.datastore.foundationdb.model.publishModelSchemaReady
import maryk.datastore.foundationdb.model.readModelSchemaState
import maryk.datastore.foundationdb.model.readStoredModelDefinition
import maryk.datastore.foundationdb.model.requireModelSchemaRebuildOwner
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.FloatingPointStorageMigration
import maryk.datastore.shared.migration.StorageFormatRow
import maryk.datastore.shared.migration.StorageFormatUpgradeStore
import maryk.datastore.shared.migration.StorageFormatWrite
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.migration.upgradeStorageFormat
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.directory.asDirectory
import maryk.datastore.foundationdb.processors.helpers.nextBlocking

private val RebuildingKey = "rebuilding".encodeToByteArray()
private const val FormatLeaseId = "storage:upgrade"

internal suspend fun FoundationDBDataStore.upgradeFoundationDBStorageFormat(preparePublication: () -> Unit) {
    val prefix = storageFormatPrefix
    if (tc.run { decodeStorageFormat(it.get(prefix + StorageFormatMetadataKey).awaitResult()) } == StorageFormat.CURRENT) return
    // Lease model id 0 is local to this isolated lease subspace, never an application-model id.
    val globalLease = FoundationDBMigrationLease(tc, mapOf(0u to prefix), this,
        migrationLeaseConfiguration.migrationLeaseTimeoutMs, migrationLeaseConfiguration.migrationLeaseHeartbeatMs)
    if (!globalLease.tryAcquire(0u, FormatLeaseId)) throw MigrationException("Another process is upgrading the store format; retry after it completes")
    val acquiredModels = mutableListOf<UInt>()
    var modelLease: FoundationDBMigrationLease? = null
    try {
        if (tc.run { decodeStorageFormat(it.get(prefix + StorageFormatMetadataKey).awaitResult()) } == StorageFormat.CURRENT) return
        val context = DefinitionsConversionContext()
        val models = linkedMapOf<UInt, IsRootDataModel>()
        val directories = linkedMapOf<UInt, IsTableDirectories>()
        val alreadyRebuilding = tc.run { it.get(prefix + RebuildingKey).awaitResult() != null }
        for ((id, name) in readStoredModelNamesById().entries.sortedBy { it.key }) {
            val historic = tc.run { rootDirectory.asDirectory().exists(it, listOf(name, "index_versioned")).awaitResult() }
            val dirs = openTableDirs(id, name, historic)
            val model = readStoredModelDefinition(tc, dirs.modelPrefix, context) ?: continue
            tc.run { transaction ->
                if (transaction.get(packKey(dirs.modelPrefix, modelMigrationStateKey)).awaitResult() != null ||
                    !alreadyRebuilding && readModelSchemaState(transaction, dirs.modelPrefix) is FoundationDBSchemaState.Rebuilding) {
                    throw MigrationException("Finish the pending schema migration for model $id before upgrading storage")
                }
                if (model.Meta.keyDefinition.requiresStorageFormatUpgrade()) {
                    throw MigrationException("Model $id uses a floating-point primary key; an explicit key/reference migration is required")
                }
            }
            models[id] = model
            directories[id] = dirs
        }
        val leases = FoundationDBMigrationLease(tc, directories.mapValues { it.value.modelPrefix }, this,
            migrationLeaseConfiguration.migrationLeaseTimeoutMs, migrationLeaseConfiguration.migrationLeaseHeartbeatMs)
        modelLease = leases
        for (id in directories.keys) {
            if (!leases.tryAcquire(id, FormatLeaseId)) throw MigrationException("Another process is migrating model $id; retry after it completes")
            acquiredModels += id
        }
        tc.run { transaction ->
            globalLease.requireOwnership(transaction, 0u, FormatLeaseId)
            directories.keys.forEach { leases.requireOwnership(transaction, it, FormatLeaseId) }
            transaction.set(prefix + RebuildingKey, byteArrayOf(1))
        }
        val fences = directories.mapValues { (id, dirs) ->
            beginModelSchemaRebuild(tc, dirs.modelPrefix, models.getValue(id)) { transaction ->
                globalLease.requireOwnership(transaction, 0u, FormatLeaseId)
                leases.requireOwnership(transaction, id, FormatLeaseId)
            }
        }
        val guard: (Transaction) -> Unit = { transaction ->
            globalLease.requireOwnership(transaction, 0u, FormatLeaseId)
            directories.forEach { (id, dirs) ->
                leases.requireOwnership(transaction, id, FormatLeaseId)
                transaction.requireModelSchemaRebuildOwner(dirs.modelPrefix, fences.getValue(id))
            }
        }
        storageFormatTransactionGuard = guard
        val namespaces = directories.entries.flatMap { (id, dirs) ->
            if (!models.getValue(id).hasFloatingPointStorage()) emptyList() else
                listOf(FormatIndexNamespace(id, dirs.tablePrefix, false, "table")) +
                    if (dirs is HistoricTableDirectories) listOf(FormatIndexNamespace(id, dirs.historicTablePrefix, true, "table")) else emptyList()
        } + directories.entries.flatMap { (id, dirs) ->
            (if (!models.getValue(id).hasFloatingPointStorage()) emptyList() else
                listOf(FormatIndexNamespace(id, dirs.uniquePrefix, false, "unique")) +
                    if (dirs is HistoricTableDirectories) listOf(FormatIndexNamespace(id, dirs.historicUniquePrefix, true, "unique")) else emptyList()) +
            (if (models.getValue(id).Meta.indexes.orEmpty().none { it.requiresStorageFormatUpgrade() }) emptyList() else
                listOf(FormatIndexNamespace(id, dirs.indexPrefix, false, "index")) +
                    if (dirs is HistoricTableDirectories) listOf(FormatIndexNamespace(id, dirs.historicIndexPrefix, true, "index")) else emptyList())
        }
        val adapter = object : StorageFormatUpgradeStore {
            override val namespaceCount = namespaces.size
            override val identity = namespaces.joinToString(",") { "${it.modelId}:${it.kind}:${it.historic}" }
            private fun namespacePrefix(namespace: Int) = if (namespace == 0) prefix else namespaces[namespace - 1].prefix
            override suspend fun get(namespace: Int, key: ByteArray) = tc.run { transaction ->
                guard(transaction)
                transaction.get(namespacePrefix(namespace) + key).awaitResult()
            }
            override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt) = tc.run { transaction ->
                guard(transaction)
                val scope = namespacePrefix(namespace)
                val range = Range.startsWith(scope)
                val begin = if (after == null) range.begin else scope + after + byteArrayOf(0)
                val iterator = transaction.getRange(Range(begin, range.end), limit.toInt(), false).iterator()
                buildList {
                    while (iterator.hasNext()) {
                        val entry = iterator.nextBlocking()
                        add(StorageFormatRow(entry.key.copyOfRange(scope.size, entry.key.size), entry.value))
                    }
                }
            }
            override suspend fun commit(writes: List<StorageFormatWrite>) {
                tc.run { transaction ->
                    guard(transaction)
                    writes.forEach { write ->
                        val key = namespacePrefix(write.namespace) + write.key
                        val value = write.value
                        require(key.size <= 10_000 && (value?.size ?: 0) <= 100_000) { "Converted index exceeds FoundationDB key/value limits" }
                        if (value == null) transaction.clear(key) else transaction.set(key, value)
                    }
                    if (writes.any { it.namespace == 0 && it.key.contentEquals(StorageFormatMetadataKey) }) {
                        directories.forEach { (id, dirs) -> transaction.publishModelSchemaReady(dirs.modelPrefix, fences.getValue(id)) }
                        transaction.clear(prefix + RebuildingKey)
                    }
                }
            }
            override suspend fun preparePublication() = preparePublication.invoke()
            private val values = models.mapValues { (id, model) -> FloatingPointStorageMigration(id, model, this, fieldEncryptionProvider) }
            override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
                val source = namespaces[namespace - 1]
                val model = models.getValue(source.modelId)
                val migration = values.getValue(source.modelId)
                if (source.kind == "table") {
                    if (row.key.size <= model.Meta.keyByteSize) return row
                    val record = row.key.copyOfRange(0, model.Meta.keyByteSize)
                    val end = row.key.size - if (source.historic) 9 else 0
                    val qualifier = if (source.historic) decodeZeroFreeUsing01(row.key, record.size, end - record.size) else row.key.copyOfRange(record.size, end)
                    val valueOffset = if (source.historic) 0 else 8
                    val converted = migration.convertCell(record, qualifier, row.value.copyOfRange(valueOffset, row.value.size))
                    val target = record + (if (source.historic) encodeZeroFreeUsing01(converted.key) else converted.key) + row.key.copyOfRange(end, row.key.size)
                    return StorageFormatRow(target, row.value.copyOfRange(0, valueOffset) + converted.value)
                }
                if (source.kind == "unique") {
                    val end = row.key.size - if (source.historic) 9 else 0
                    val content = if (source.historic) decodeZeroFreeUsing01(row.key, 0, end) else row.key
                    val converted = migration.convertUnique(content)
                    return StorageFormatRow((if (source.historic) encodeZeroFreeUsing01(converted) else converted) + row.key.copyOfRange(end, row.key.size), row.value)
                }
                val end = row.key.size - if (source.historic) 9 else 0
                require(end >= 0 && (!source.historic || row.key[end] == 0.toByte())) { "Invalid historic index key" }
                val content = if (source.historic) decodeZeroFreeUsing01(row.key, 0, end) else row.key
                val index = model.Meta.indexes.orEmpty().sortedByDescending { it.referenceStorageByteArray.bytes.size }
                    .firstOrNull { candidate -> content.hasPrefix(candidate.referenceStorageByteArray.bytes) } ?: return row
                if (!index.requiresStorageFormatUpgrade()) return row
                val reference = index.referenceStorageByteArray.bytes
                val converted = reference + index.convertLegacyIndexBytes(content.copyOfRange(reference.size, content.size), model.Meta.keyByteSize)
                val target = if (source.historic) encodeZeroFreeUsing01(converted) + row.key.copyOfRange(end, row.key.size) else converted
                require(source.prefix.size + target.size <= 10_000) { "Converted index exceeds FoundationDB's key limit" }
                return StorageFormatRow(target, row.value)
            }
        }
        adapter.upgradeStorageFormat()
    } finally {
        storageFormatTransactionGuard = null
        acquiredModels.asReversed().forEach { modelLease?.release(it, FormatLeaseId) }
        globalLease.release(0u, FormatLeaseId)
    }
}

private data class FormatIndexNamespace(val modelId: UInt, val prefix: ByteArray, val historic: Boolean, val kind: String)
private fun ByteArray.hasPrefix(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
