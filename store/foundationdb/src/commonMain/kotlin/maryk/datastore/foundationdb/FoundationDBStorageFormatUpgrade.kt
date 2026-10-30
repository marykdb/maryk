package maryk.datastore.foundationdb

import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.requiresStorageFormatUpgrade
import maryk.core.models.migration.requiresKeyStorageFormatUpgrade
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.core.models.migration.toReleasedReferenceStorageByteArray
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.shared.loadStoredModelDefinitions
import maryk.datastore.foundationdb.model.FoundationDBMigrationLease
import maryk.datastore.foundationdb.model.FoundationDBSchemaFence
import maryk.datastore.foundationdb.model.FoundationDBSchemaState
import maryk.datastore.foundationdb.model.beginModelSchemaRebuild
import maryk.datastore.foundationdb.model.encodeModelDefinition
import maryk.datastore.foundationdb.model.modelMigrationStateKey
import maryk.datastore.foundationdb.model.publishModelSchemaReady
import maryk.datastore.foundationdb.model.readModelSchemaState
import maryk.datastore.foundationdb.model.readStoredModelDefinition
import maryk.datastore.foundationdb.model.requireModelSchemaRebuildOwner
import maryk.datastore.foundationdb.model.storeModelDefinition
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.datastore.shared.migration.FloatingPointStorageMigration
import maryk.datastore.shared.migration.hasLegacyCollectionStorage
import maryk.datastore.shared.migration.isLegacyZeroCollectionCount
import maryk.datastore.shared.migration.ambiguousLegacyCollectionCount
import maryk.datastore.shared.StoredPropertyDeleteMarker
import maryk.datastore.shared.migration.LegacyIncrementingMapRecovery
import maryk.datastore.shared.migration.StorageFormatRow
import maryk.datastore.shared.migration.StorageFormatUpgradeStore
import maryk.datastore.shared.migration.StorageFormatWrite
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.migration.upgradeStorageFormat
import maryk.datastore.shared.tagReleasedTransactionReceipt
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.directory.asDirectory
import maryk.foundationdb.tuple.Tuple
import maryk.datastore.foundationdb.processors.helpers.nextBlocking

private val RebuildingKey = "rebuilding".encodeToByteArray()
private const val FormatLeaseId = "storage:upgrade"

internal suspend fun FoundationDBDataStore.upgradeFoundationDBStorageFormat(preparePublication: () -> Unit) {
    val prefix = storageFormatPrefix
    if (tc.run { decodeStorageFormat(it.get(prefix + StorageFormatMetadataKey).awaitResult(), it.get(prefix + StorageFormatUpgradeStateKey).awaitResult()) } == StorageFormat.CURRENT) return
    // Lease model id 0 is local to this isolated lease subspace, never an application-model id.
    val globalLease = FoundationDBMigrationLease(tc, mapOf(0u to prefix), this,
        migrationLeaseConfiguration.migrationLeaseTimeoutMs, migrationLeaseConfiguration.migrationLeaseHeartbeatMs)
    if (!globalLease.tryAcquire(0u, FormatLeaseId)) throw MigrationException("Another process is upgrading the store format; retry after it completes")
    val acquiredModels = mutableListOf<UInt>()
    var modelLease: FoundationDBMigrationLease? = null
    try {
        if (tc.run { decodeStorageFormat(it.get(prefix + StorageFormatMetadataKey).awaitResult(), it.get(prefix + StorageFormatUpgradeStateKey).awaitResult()) } == StorageFormat.CURRENT) return
        val context = StoredModelDefinitionsContext()
        val models = linkedMapOf<UInt, IsRootDataModel>()
        val directories = linkedMapOf<UInt, IsTableDirectories>()
        val alreadyRebuilding = tc.run { it.get(prefix + RebuildingKey).awaitResult() != null }
        val storedModels = loadStoredModelDefinitions(readStoredModelNamesById().entries.sortedBy { it.key }) { (id, name) ->
            val historic = tc.run { rootDirectory.asDirectory().exists(it, listOf(name, "index_versioned")).awaitResult() }
            val dirs = openTableDirs(id, name, historic)
            val model = readStoredModelDefinition(tc, dirs.modelPrefix, context, legacyStorage = true)
                ?: return@loadStoredModelDefinitions null
            tc.run { transaction ->
                if (transaction.get(packKey(dirs.modelPrefix, modelMigrationStateKey)).awaitResult() != null ||
                    !alreadyRebuilding && readModelSchemaState(transaction, dirs.modelPrefix) is FoundationDBSchemaState.Rebuilding) {
                    throw MigrationException("Finish the pending schema migration for model $id before upgrading storage")
                }
            }
            directories[id] = dirs
            model
        }
        for ((entry, model) in storedModels) {
            if (model.Meta.keyDefinition.requiresKeyStorageFormatUpgrade()) {
                throw MigrationException("Model ${entry.key} uses a primary key whose storage encoding changes; an explicit key/reference migration is required")
            }
            models[entry.key] = model
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
        val chunkedMetadata = FoundationDBStorageFormatMetadata(tc.run { transaction ->
            guard(transaction)
            rootDirectory.createOrOpen(transaction, listOf("__meta__", "storage_format_chunks")).awaitResult().pack()
        })
        val receiptPrefix = tc.run { transaction ->
            guard(transaction)
            rootDirectory.open(transaction, listOf("__meta__", "atomic_receipts")).awaitResult().pack()
        }
        val namespaces = directories.entries.flatMap { (id, dirs) ->
            if (!models.getValue(id).let { it.hasFloatingPointStorage() || it.hasLegacyCollectionStorage() }) emptyList() else
                listOf(FormatIndexNamespace(id, dirs.tablePrefix, false, "table")) +
                    if (dirs is HistoricTableDirectories) listOf(FormatIndexNamespace(id, dirs.historicTablePrefix, true, "table")) else emptyList()
        } + directories.entries.flatMap { (id, dirs) ->
            (if (!models.getValue(id).hasFloatingPointStorage()) emptyList() else
                listOf(FormatIndexNamespace(id, dirs.uniquePrefix, false, "unique")) +
                    if (dirs is HistoricTableDirectories) listOf(FormatIndexNamespace(id, dirs.historicUniquePrefix, true, "unique")) else emptyList()) +
            (if (models.getValue(id).Meta.indexes.orEmpty().none { it.requiresStorageFormatUpgrade() }) emptyList() else
                listOf(FormatIndexNamespace(id, dirs.indexPrefix, false, "index")) +
                    if (dirs is HistoricTableDirectories) listOf(FormatIndexNamespace(id, dirs.historicIndexPrefix, true, "index")) else emptyList())
        } + FormatIndexNamespace(0u, receiptPrefix, false, "receipts")
        val adapter = object : StorageFormatUpgradeStore {
            override val namespaceCount = namespaces.size
            override val identity = namespaces.joinToString(",") { "${it.modelId}:${it.kind}:${it.historic}" } + ",collection-count-proof-v1,allocation-proof-v1"
            private fun namespacePrefix(namespace: Int) = if (namespace == 0) prefix else namespaces[namespace - 1].prefix
            override suspend fun get(namespace: Int, key: ByteArray) = tc.run { transaction ->
                guard(transaction)
                val value = transaction.get(namespacePrefix(namespace) + key).awaitResult()
                if (namespace == 0) chunkedMetadata.read(transaction, key, value) else value
            }
            override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt) = tc.run { transaction ->
                guard(transaction)
                val scope = namespacePrefix(namespace)
                val range = Range.startsWith(scope)
                if (namespace != 0 && namespaces[namespace - 1].kind == "receipts") {
                    var begin = if (after == null) range.begin else Range.startsWith(scope + after).end
                    return@run buildList {
                        while (size.toUInt() < limit) {
                            val iterator = transaction.getRange(Range(begin, range.end), 1, false).iterator()
                            if (!iterator.hasNext()) break
                            val row = iterator.nextBlocking()
                            val key = row.key.copyOfRange(scope.size, row.key.size)
                            requireReceiptManifestId(key)
                            add(StorageFormatRow(key, row.value))
                            begin = Range.startsWith(row.key).end
                        }
                    }
                }
                val begin = if (after == null) range.begin else scope + after + byteArrayOf(0)
                val iterator = transaction.getRange(Range(begin, range.end), limit.toInt(), false).iterator()
                buildList {
                    while (iterator.hasNext()) {
                        val entry = iterator.nextBlocking()
                        val key = entry.key.copyOfRange(scope.size, entry.key.size)
                        val value = if (namespace == 0) chunkedMetadata.read(transaction, key, entry.value)!! else entry.value
                        add(StorageFormatRow(key, value))
                    }
                }
            }
            override suspend fun commit(writes: List<StorageFormatWrite>) {
                tc.run { transaction ->
                    guard(transaction)
                    writes.forEach { write ->
                        val key = namespacePrefix(write.namespace) + write.key
                        val value = write.value
                        require(key.size <= 10_000 && (write.namespace == 0 || (value?.size ?: 0) <= 100_000)) { "Converted index exceeds FoundationDB key/value limits" }
                        if (write.namespace == 0) chunkedMetadata.write(transaction, prefix, write.key, value)
                        else if (value == null) transaction.clear(key) else transaction.set(key, value)
                    }
                    if (writes.any { it.namespace == 0 && it.key.contentEquals(StorageFormatMetadataKey) }) {
                        directories.forEach { (id, dirs) -> transaction.publishModelSchemaReady(dirs.modelPrefix, fences.getValue(id)) }
                        chunkedMetadata.clear(transaction)
                        transaction.clear(prefix + RebuildingKey)
                    }
                }
            }
            override suspend fun preparePublication() {
                // Keep each source definition pair readable after an interrupted publication.
                // Configured target schemas belong to the later application migration.
                val modelNamesPrefix = tc.run { transaction ->
                    guard(transaction)
                    rootDirectory.open(transaction, listOf("__meta__", "models_by_id")).awaitResult().pack()
                }
                for ((id, model) in models) {
                    val definition = encodeModelDefinition(model)
                    tc.run { transaction ->
                        guard(transaction)
                        storeModelDefinition(transaction, modelNamesPrefix, id, directories.getValue(id).modelPrefix,
                            model.Meta.name, definition)
                    }
                }
                preparePublication.invoke()
            }
            private val values = models.mapValues { (id, model) -> FloatingPointStorageMigration(id, model, this, fieldEncryptionProvider) }
            private val allocations = models.mapValues { (id, model) ->
                val scope = directories.getValue(id).modelPrefix
                LegacyIncrementingMapRecovery(id, model,
                    read = { key -> tc.run { transaction ->
                        guard(transaction)
                        transaction.get(scope + key).awaitResult()
                    } },
                    write = { key, value -> tc.run { transaction ->
                        guard(transaction)
                        require(scope.size + key.size <= 10_000 && value.size <= 100_000) { "Recovered allocation watermark exceeds FoundationDB limits" }
                        transaction.set(scope + key, value)
                    } })
            }
            override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
                val converted = convertRow(namespace, row)
                val source = namespaces[namespace - 1]
                val keySize = namespacePrefix(namespace).size + converted.key.size
                if (keySize > 10_000 || converted.value.size > 100_000) {
                    throw MigrationException("Converted storage row exceeds FoundationDB key/value limits: " +
                        "model=${source.modelId} namespace=${source.kind} historic=${source.historic} " +
                        "keyBytes=$keySize valueBytes=${converted.value.size}. Source rows are unchanged; " +
                        "recover with an encoding/encryption provider whose destination rows fit the native limits before reopening.")
                }
                return converted
            }

            private suspend fun convertRow(namespace: Int, row: StorageFormatRow): StorageFormatRow {
                val source = namespaces[namespace - 1]
                if (source.kind == "receipts") {
                    val id = requireReceiptManifestId(row.key)
                    val receipt = tc.run { transaction ->
                        guard(transaction)
                        readAtomicReceipt(transaction, source.prefix, id)
                    } ?: throw MigrationException("Missing transaction receipt during storage conversion")
                    val tagged = tagReleasedTransactionReceipt(receipt)
                    return StorageFormatRow(row.key, tagged.copyOfRange(0, tagged.size - receipt.size) + row.value)
                }
                val model = models.getValue(source.modelId)
                val migration = values.getValue(source.modelId)
                if (source.kind == "table") {
                    if (row.key.size <= model.Meta.keyByteSize) {
                        if (!source.historic && row.key.size == model.Meta.keyByteSize) {
                            val record = row.key
                            val dirs = directories.getValue(source.modelId)
                            val creation = tc.run { transaction ->
                                guard(transaction)
                                transaction.get(dirs.keysPrefix + record).awaitResult()
                            }
                            allocations.getValue(source.modelId).verify(record, creation, row.value, verifyContinuousHistory = history@ {
                                if (dirs !is HistoricTableDirectories || creation == null || row.value.size != 8) return@history false
                                val scope = dirs.historicTablePrefix + record
                                if (tc.run { transaction -> guard(transaction); transaction.get(scope).awaitResult() }?.contentEquals(creation) != true) return@history false
                                var frontier = creation.toHexString()
                                var begin = scope
                                val end = Range.startsWith(scope).end
                                while (true) {
                                    val batch = tc.run { transaction ->
                                        guard(transaction)
                                        val iterator = transaction.getRange(Range(begin, end), 64, false).iterator()
                                        buildList { while (iterator.hasNext()) add(iterator.nextBlocking()) }
                                    }
                                    if (batch.isEmpty()) break
                                    for (entry in batch) {
                                        if (entry.key.size == scope.size) continue
                                        val qualifierEnd = entry.key.size - 9
                                        if (qualifierEnd < scope.size) return@history false
                                        val version = ByteArray(8) { entry.key[entry.key.size - 8 + it].toInt().inv().toByte() }.toHexString()
                                        if (version < creation.toHexString() || version > row.value.toHexString()) return@history false
                                        frontier = maxOf(frontier, version)
                                        val qualifier = decodeZeroFreeUsing01(entry.key, scope.size, qualifierEnd - scope.size)
                                        allocations.getValue(source.modelId).observe(record, qualifier)
                                    }
                                    begin = batch.last().key + byteArrayOf(0)
                                }
                                frontier == row.value.toHexString()
                            })
                        }
                        return row
                    }
                    val record = row.key.copyOfRange(0, model.Meta.keyByteSize)
                    val end = row.key.size - if (source.historic) 9 else 0
                    val qualifier = if (source.historic) decodeZeroFreeUsing01(row.key, record.size, end - record.size) else row.key.copyOfRange(record.size, end)
                    allocations.getValue(source.modelId).observe(record, qualifier)
                    val valueOffset = if (source.historic) 0 else 8
                    var payload = row.value.copyOfRange(valueOffset, row.value.size)
                    if (model.isLegacyZeroCollectionCount(qualifier, payload)) {
                        val version = if (source.historic) ByteArray(8) { row.key[row.key.size - 8 + it].toInt().inv().toByte() }
                            else row.value.copyOfRange(0, valueOffset)
                        val dirs = directories.getValue(source.modelId)
                        val creation = tc.run { transaction ->
                            guard(transaction)
                            transaction.get(dirs.keysPrefix + record).awaitResult()
                        }
                        if (creation?.contentEquals(version) != true) {
                            // Released FDB historic [0] means either deletion or an empty
                            // collection. Only the distinct empty-payload marker proves deletion.
                            val evidence = if (source.historic || dirs !is HistoricTableDirectories) null else tc.run { transaction ->
                                guard(transaction)
                                transaction.get(packVersionedKey(dirs.historicTablePrefix, record,
                                    encodeZeroFreeUsing01(qualifier), version = version)).awaitResult()
                            }
                            if (evidence?.isEmpty() == true) payload = StoredPropertyDeleteMarker
                            else ambiguousLegacyCollectionCount(source.modelId, qualifier, version)
                        }
                    }
                    val converted = migration.convertCell(record, qualifier, payload)
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
                val index = model.Meta.indexes.orEmpty().sortedByDescending { it.toReleasedReferenceStorageByteArray().size }
                    .firstOrNull { candidate -> content.hasPrefix(candidate.toReleasedReferenceStorageByteArray()) } ?: return row
                if (!index.requiresStorageFormatUpgrade()) return row
                val reference = index.referenceStorageByteArray.bytes
                val oldReference = index.toReleasedReferenceStorageByteArray()
                val converted = reference + index.convertLegacyIndexBytes(content.copyOfRange(oldReference.size, content.size), model.Meta.keyByteSize)
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

private fun requireReceiptManifestId(key: ByteArray): String {
    val fields = Tuple.fromBytes(key)
    if (fields.size != 1 || fields[0] !is String) throw MigrationException("Invalid transaction receipt manifest identity")
    return fields[0] as String
}
