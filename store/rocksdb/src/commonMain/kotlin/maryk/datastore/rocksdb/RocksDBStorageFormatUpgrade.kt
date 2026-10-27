package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.decodeVarUInt
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.requiresStorageFormatUpgrade
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.rocksdb.metadata.StoreMeta
import maryk.datastore.rocksdb.metadata.writeStoreMetaFile
import maryk.datastore.rocksdb.model.modelMigrationStateKey
import maryk.datastore.rocksdb.model.readStoredModelDefinition
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.FloatingPointStorageMigration
import maryk.datastore.shared.migration.StorageFormatRow
import maryk.datastore.shared.migration.StorageFormatUpgradeStore
import maryk.datastore.shared.migration.StorageFormatWrite
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.migration.upgradeStorageFormat

private val LegacyIndexRebuildKey = "maryk.storage/rebuild-legacy-indexes".encodeToByteArray()

/** Runs under RocksDB's exclusive database lock, before requests or schema hooks start. */
internal suspend fun RocksDBDataStore.upgradeRocksStorageFormat(
    storePath: String,
    storeMeta: StoreMeta,
    preparePublication: suspend (Boolean) -> Unit,
) {
    val handles = storageUpgradeHandles
    val metadata = handles.first()
    if (decodeStorageFormat(db.get(metadata, StorageFormatMetadataKey)) == StorageFormat.CURRENT) return
    val context = DefinitionsConversionContext()
    val models = linkedMapOf<UInt, IsRootDataModel>()
    val familyInfo = handles.drop(1).mapNotNull { handle ->
        val name = handle.getName()
        val type = TableType.entries.firstOrNull { it.byte == name.firstOrNull() } ?: return@mapNotNull null
        val modelId = name.decodeVarUInt(startIndex = 1) ?: return@mapNotNull null
        Triple(modelId, type, handle)
    }
    for ((id, type, handle) in familyInfo) {
        if (type != TableType.Model) continue
        if (db.get(handle, modelMigrationStateKey) != null) {
            throw MigrationException("Finish the pending schema migration for model $id with its source-format version before upgrading storage")
        }
        readStoredModelDefinition(db, handle, context)?.let { models[id] = it }
    }
    for ((id, model) in models) {
        if (model.Meta.keyDefinition.requiresStorageFormatUpgrade()) {
            throw MigrationException("Model $id uses a floating-point primary key; an explicit key/reference migration is required")
        }
    }
    val rebuildLegacyIndexes = storeMeta.indexKeyFormatVersion < 2 || db.get(metadata, LegacyIndexRebuildKey) != null
    val namespaces = familyInfo.filter { (id, type, _) ->
        val model = models[id]
        when (type) {
            TableType.Table, TableType.HistoricTable, TableType.Unique, TableType.HistoricUnique -> model?.hasFloatingPointStorage() == true
            TableType.Index, TableType.HistoricIndex -> !rebuildLegacyIndexes && model?.Meta?.indexes.orEmpty().any { it.requiresStorageFormatUpgrade() }
            else -> false
        }
    }.sortedWith(compareBy({ if (it.second == TableType.Unique || it.second == TableType.HistoricUnique) 1 else 0 }, { it.first }, { it.second.byte }))
    // Old binaries already recognize and reject a newer metadata-envelope version.
    writeStoreMetaFile(storePath, storeMeta)
    if (rebuildLegacyIndexes) db.put(metadata, LegacyIndexRebuildKey, byteArrayOf(1))
    val converter = object : StorageFormatUpgradeStore {
        override val namespaceCount = namespaces.size
        override val identity = namespaces.joinToString(",") { "${it.first}:${it.second.byte}" }
        private fun handle(namespace: Int) = if (namespace == 0) metadata else namespaces[namespace - 1].third
        override suspend fun get(namespace: Int, key: ByteArray) = db.get(handle(namespace), key)
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt): List<StorageFormatRow> {
            val rows = mutableListOf<StorageFormatRow>()
            db.newIterator(handle(namespace), sequentialReadOptions).use { iterator ->
                if (after == null) iterator.seekToFirst() else iterator.seek(after)
                while (iterator.isValid() && rows.size.toUInt() < limit) {
                    val key = iterator.key()
                    if (after == null || !key.contentEquals(after)) rows += StorageFormatRow(key, iterator.value())
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
        override suspend fun preparePublication() = preparePublication.invoke(rebuildLegacyIndexes)
        private val values = models.mapValues { (id, model) -> FloatingPointStorageMigration(id, model, this, fieldEncryptionProvider) }
        override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
            val (id, type) = namespaces[namespace - 1]
            val model = models.getValue(id)
            if (type == TableType.Table || type == TableType.HistoricTable) {
                val historic = type == TableType.HistoricTable
                if (row.key.size <= model.Meta.keyByteSize) return row
                val end = row.key.size - if (historic) 8 else 0
                val record = row.key.copyOfRange(0, model.Meta.keyByteSize)
                val qualifier = row.key.copyOfRange(record.size, end)
                val valueOffset = if (historic) 0 else 8
                val converted = values.getValue(id).convertCell(record, qualifier, row.value.copyOfRange(valueOffset, row.value.size))
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
            val index = model.Meta.indexes.orEmpty().firstOrNull { it.referenceStorageByteArray.bytes.contentEquals(reference) }
                ?: return row
            if (!index.requiresStorageFormatUpgrade()) return row
            val end = row.key.size - if (type == TableType.HistoricIndex) 8 else 0
            val converted = index.convertLegacyIndexBytes(row.key.copyOfRange(offset, end), model.Meta.keyByteSize)
            return StorageFormatRow(row.key.copyOfRange(0, offset) + converted + row.key.copyOfRange(end, row.key.size), row.value)
        }
    }
    converter.upgradeStorageFormat()
}
