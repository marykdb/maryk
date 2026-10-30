package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.decodeVarUInt
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.requiresStorageFormatUpgrade
import maryk.core.models.migration.requiresKeyStorageFormatUpgrade
import maryk.core.models.migration.toReleasedReferenceStorageByteArray
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.shared.loadStoredModelDefinitions
import maryk.datastore.rocksdb.metadata.StoreMeta
import maryk.datastore.rocksdb.metadata.writeStoreMetaFile
import maryk.datastore.rocksdb.model.modelMigrationStateKey
import maryk.datastore.rocksdb.model.readStoredModelDefinition
import maryk.datastore.rocksdb.model.rewriteStoredModelDefinition
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.datastore.shared.migration.FloatingPointStorageMigration
import maryk.datastore.shared.migration.hasLegacyCollectionStorage
import maryk.datastore.shared.migration.isLegacyZeroCollectionCount
import maryk.datastore.shared.migration.ambiguousLegacyCollectionCount
import maryk.datastore.shared.StoredPropertyDeleteMarker
import maryk.datastore.shared.migration.LegacyIncrementingMapRecovery
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
import maryk.datastore.shared.migration.StorageFormatRow
import maryk.datastore.shared.migration.StorageFormatUpgradeStore
import maryk.datastore.shared.migration.StorageFormatWrite
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.migration.upgradeStorageFormat
import maryk.datastore.shared.tagReleasedTransactionReceipt

private val LegacyIndexRebuildKey = "maryk.storage/rebuild-legacy-indexes".encodeToByteArray()

/** Runs under RocksDB's exclusive database lock, before requests or schema hooks start. */
internal suspend fun RocksDBDataStore.upgradeRocksStorageFormat(
    storePath: String,
    storeMeta: StoreMeta,
    preparePublication: suspend (Boolean) -> Unit,
) {
    val handles = storageUpgradeHandles
    val metadata = handles.first()
    if (decodeStorageFormat(db.get(metadata, StorageFormatMetadataKey), db.get(metadata, StorageFormatUpgradeStateKey)) == StorageFormat.CURRENT) return
    val context = StoredModelDefinitionsContext()
    val models = linkedMapOf<UInt, IsRootDataModel>()
    val familyInfo = handles.drop(1).mapNotNull { handle ->
        val name = handle.getName()
        val type = TableType.entries.firstOrNull { it.byte == name.firstOrNull() } ?: return@mapNotNull null
        val modelId = name.decodeVarUInt(startIndex = 1) ?: return@mapNotNull null
        Triple(modelId, type, handle)
    }
    val storedModels = loadStoredModelDefinitions(familyInfo.filter { it.second == TableType.Model }) { (id, _, handle) ->
        if (db.get(handle, modelMigrationStateKey) != null) {
            throw MigrationException("Finish the pending schema migration for model $id with its source-format version before upgrading storage")
        }
        readStoredModelDefinition(db, handle, context)
    }
    for ((identity, model) in storedModels) {
        models[identity.first] = model
    }
    for ((id, model) in models) {
        if (model.Meta.keyDefinition.requiresKeyStorageFormatUpgrade()) {
            throw MigrationException("Model $id uses a primary key whose storage encoding changes; an explicit key/reference migration is required")
        }
    }
    val rebuildLegacyIndexes = storeMeta.indexKeyFormatVersion < 2 || db.get(metadata, LegacyIndexRebuildKey) != null
    val namespaces = familyInfo.filter { (id, type, _) ->
        val model = models[id]
        when (type) {
            TableType.Table, TableType.HistoricTable -> model?.let { it.hasFloatingPointStorage() || it.hasLegacyCollectionStorage() } == true
            TableType.Unique, TableType.HistoricUnique -> model?.hasFloatingPointStorage() == true
            TableType.Index, TableType.HistoricIndex -> !rebuildLegacyIndexes && model?.Meta?.indexes.orEmpty().any { it.requiresStorageFormatUpgrade() }
            else -> false
        }
    }.sortedWith(compareBy({ if (it.second == TableType.Unique || it.second == TableType.HistoricUnique) 1 else 0 }, { it.first }, { it.second.byte }))
    // Old binaries already recognize and reject a newer metadata-envelope version.
    writeStoreMetaFile(storePath, storeMeta)
    if (rebuildLegacyIndexes) db.put(metadata, LegacyIndexRebuildKey, byteArrayOf(1))
    val converter = object : StorageFormatUpgradeStore {
        private val receiptNamespace = namespaces.size + 1
        override val namespaceCount = receiptNamespace
        override val identity = namespaces.joinToString(",") { "${it.first}:${it.second.byte}" } + ",transaction-receipts,collection-count-proof-v1,allocation-proof-v1"
        private fun handle(namespace: Int) = if (namespace == 0 || namespace == receiptNamespace) metadata else namespaces[namespace - 1].third
        override suspend fun get(namespace: Int, key: ByteArray) = db.get(handle(namespace), key)
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt): List<StorageFormatRow> {
            val rows = mutableListOf<StorageFormatRow>()
            db.newIterator(handle(namespace), sequentialReadOptions).use { iterator ->
                if (after == null && namespace == receiptNamespace) iterator.seek(TransactionReceiptPrefix)
                else if (after == null) iterator.seekToFirst() else iterator.seek(after)
                while (iterator.isValid() && rows.size.toUInt() < limit) {
                    val key = iterator.key()
                    if (namespace == receiptNamespace && !key.startsWith(TransactionReceiptPrefix)) break
                    val value = iterator.value()
                    if ((after == null || !key.contentEquals(after)) &&
                        !(namespace == receiptNamespace && key.contentEquals(LegacyTransactionRevisionKey) && value.size == 16)) {
                        rows += StorageFormatRow(key, value)
                    }
                    iterator.next()
                }
            }
            return rows
        }
        override suspend fun commit(writes: List<StorageFormatWrite>) {
            Transaction(this@upgradeRocksStorageFormat).use { transaction ->
                writes.forEach { write ->
                    val value = write.value
                    if (value == null) transaction.delete(handle(write.namespace), write.key)
                    else transaction.put(handle(write.namespace), write.key, value)
                }
                if (writes.any { it.namespace == 0 && it.key.contentEquals(StorageFormatMetadataKey) }) {
                    transaction.delete(metadata, LegacyIndexRebuildKey)
                }
                transaction.commit()
            }
        }
        override suspend fun preparePublication() {
            Transaction(this@upgradeRocksStorageFormat).use { transaction ->
                val oldRevision = transaction.get(metadata, defaultReadOptions, LegacyTransactionRevisionKey)
                if (oldRevision?.size == 16) {
                    if (transaction.get(metadata, defaultReadOptions, TransactionRevisionKey) == null) {
                        transaction.put(metadata, TransactionRevisionKey, oldRevision)
                    }
                    transaction.delete(metadata, LegacyTransactionRevisionKey)
                    transaction.commit()
                }
            }
            for ((id, type, handle) in familyInfo) {
                if (type == TableType.Model) models[id]?.let { rewriteStoredModelDefinition(db, handle, it) }
            }
            preparePublication.invoke(rebuildLegacyIndexes)
        }
        private val values = models.mapValues { (id, model) -> FloatingPointStorageMigration(id, model, this, fieldEncryptionProvider) }
        private val allocations = models.mapValues { (id, model) ->
            val modelHandle = familyInfo.first { it.first == id && it.second == TableType.Model }.third
            LegacyIncrementingMapRecovery(id, model,
                read = { key -> db.get(modelHandle, key) },
                write = { key, value -> db.put(modelHandle, key, value) })
        }
        override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
            if (namespace == receiptNamespace) return StorageFormatRow(row.key, tagReleasedTransactionReceipt(row.value))
            val (id, type) = namespaces[namespace - 1]
            val model = models.getValue(id)
            if (type == TableType.Table || type == TableType.HistoricTable) {
                val historic = type == TableType.HistoricTable
                if (row.key.size <= model.Meta.keyByteSize) {
                    if (!historic && row.key.size == model.Meta.keyByteSize) {
                        val record = row.key
                        val creation = familyInfo.firstOrNull { it.first == id && it.second == TableType.Keys }?.third?.let { db.get(it, record) }
                        val latest = db.get(namespaces[namespace - 1].third, record + LAST_VERSION_INDICATOR)
                        allocations.getValue(id).verify(record, creation, latest, verifyContinuousHistory = history@ {
                            val history = familyInfo.firstOrNull { it.first == id && it.second == TableType.HistoricTable }?.third ?: return@history false
                            if (creation == null || latest == null || db.get(history, record)?.contentEquals(creation) != true) return@history false
                            var frontier = creation.toHexString()
                            db.newIterator(history, sequentialReadOptions).use { iterator ->
                                iterator.seek(record)
                                while (iterator.isValid()) {
                                    val key = iterator.key()
                                    if (!key.startsWith(record)) break
                                    if (key.size > record.size) {
                                        if (key.size < record.size + 8) return@history false
                                        val end = key.size - 8
                                        val version = ByteArray(8) { key[end + it].toInt().inv().toByte() }.toHexString()
                                        if (version < creation.toHexString() || version > latest.toHexString()) return@history false
                                        frontier = maxOf(frontier, version)
                                        allocations.getValue(id).observe(record, key.copyOfRange(record.size, end))
                                    }
                                    iterator.next()
                                }
                            }
                            frontier == latest.toHexString()
                        })
                    }
                    return row
                }
                val end = row.key.size - if (historic) 8 else 0
                val record = row.key.copyOfRange(0, model.Meta.keyByteSize)
                val qualifier = row.key.copyOfRange(record.size, end)
                allocations.getValue(id).observe(record, qualifier)
                val valueOffset = if (historic) 0 else 8
                var payload = row.value.copyOfRange(valueOffset, row.value.size)
                if (!historic && model.isLegacyZeroCollectionCount(qualifier, payload)) {
                    val version = row.value.copyOfRange(0, valueOffset)
                    val creation = familyInfo.firstOrNull { it.first == id && it.second == TableType.Keys }
                        ?.third?.let { db.get(it, record) }
                    if (creation?.contentEquals(version) != true) {
                        val historicHandle = familyInfo.firstOrNull { it.first == id && it.second == TableType.HistoricTable }?.third
                        val inverted = ByteArray(version.size) { version[it].toInt().inv().toByte() }
                        val evidence = historicHandle?.let { db.get(it, record + qualifier + inverted) }
                        payload = when {
                            evidence?.isEmpty() == true -> StoredPropertyDeleteMarker
                            evidence?.contentEquals(byteArrayOf(0)) == true -> payload
                            else -> ambiguousLegacyCollectionCount(id, qualifier, version)
                        }
                    }
                }
                val converted = values.getValue(id).convertCell(record, qualifier, payload)
                return StorageFormatRow(record + converted.key + row.key.copyOfRange(end, row.key.size), row.value.copyOfRange(0, valueOffset) + converted.value)
            }
            if (type == TableType.Unique || type == TableType.HistoricUnique) {
                val end = row.key.size - if (type == TableType.HistoricUnique) 8 else 0
                return StorageFormatRow(values.getValue(id).convertUnique(row.key.copyOfRange(0, end)) + row.key.copyOfRange(end, row.key.size), row.value)
            }
            var offset = 0
            val referenceLength = initIntByVar { row.key[offset++] }
            require(referenceLength >= 0 && referenceLength <= row.key.size - offset) { "Invalid index reference length" }
            val reference = row.key.copyOfRange(offset, offset + referenceLength)
            offset += referenceLength
            val index = model.Meta.indexes.orEmpty().firstOrNull { it.toReleasedReferenceStorageByteArray().contentEquals(reference) }
                ?: return row
            if (!index.requiresStorageFormatUpgrade()) return row
            val end = row.key.size - if (type == TableType.HistoricIndex) 8 else 0
            val converted = index.convertLegacyIndexBytes(row.key.copyOfRange(offset, end), model.Meta.keyByteSize)
            val currentReference = index.referenceStorageByteArray.bytes
            var referenceOffset = 0
            val currentPrefix = ByteArray(currentReference.size.calculateVarByteLength() + currentReference.size)
            currentReference.size.writeVarBytes { currentPrefix[referenceOffset++] = it }
            currentReference.copyInto(currentPrefix, referenceOffset)
            return StorageFormatRow(currentPrefix + converted + row.key.copyOfRange(end, row.key.size), row.value)
        }
    }
    converter.upgradeStorageFormat()
}

private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
