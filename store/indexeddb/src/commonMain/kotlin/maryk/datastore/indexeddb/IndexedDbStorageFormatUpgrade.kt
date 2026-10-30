package maryk.datastore.indexeddb

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.hasLegacyTransportStorage
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.requiresStorageFormatUpgrade
import maryk.core.models.migration.requiresKeyStorageFormatUpgrade
import maryk.core.models.migration.toReleasedReferenceStorageByteArray
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.shared.loadStoredModelDefinitions
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.migration.FloatingPointStorageMigration
import maryk.datastore.shared.migration.hasLegacyCollectionStorage
import maryk.datastore.shared.migration.LegacyIncrementingMapRecovery
import maryk.core.clock.HLC
import maryk.datastore.shared.migration.StorageFormatRow
import maryk.datastore.indexeddb.processors.decodeVersionedChange
import maryk.datastore.indexeddb.processors.encodeVersionedChange
import maryk.datastore.indexeddb.processors.isUnserializableChangeLogMarker
import maryk.datastore.indexeddb.processors.isHardDeleteTombstone
import maryk.datastore.indexeddb.processors.isUpdateHistoryRowKey
import maryk.datastore.indexeddb.processors.readInvertedVersion
import maryk.datastore.indexeddb.processors.readTrailingVersion
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeHistoricSnapshot
import maryk.datastore.shared.migration.StorageFormatUpgradeStore
import maryk.datastore.shared.migration.StorageFormatWrite
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.migration.upgradeStorageFormat
import maryk.datastore.shared.tagReleasedTransactionReceipt
import maryk.lib.extensions.compare.nextByteInSameLength

private const val ReceiptNamespace = "receipts"
private val ReceiptPrefix = "maryk.transaction/".encodeToByteArray()

/** Invoked under the existing startup writer fence, before schema callbacks. */
internal suspend fun IndexedDbByteStore.upgradeIndexedDbStorageFormat(
    keepAllVersions: Boolean,
    keepUpdateHistoryIndex: Boolean,
    fieldEncryptionProvider: FieldEncryptionProvider?,
    preparePublication: suspend () -> Unit,
) {
    if (decodeStorageFormat(get("meta", StorageFormatMetadataKey), get("meta", StorageFormatUpgradeStateKey)) == StorageFormat.CURRENT) return
    val options = get("meta", OptionsMetadataKey)?.decodeToString()
    val expected = indexedDbOptionsSummary(keepAllVersions, keepUpdateHistoryIndex)
    if (options != null && options != expected && options != expected.replace("schemaVersion=3", "schemaVersion=2")) {
        throw MigrationException("IndexedDB store options differ from the configured options")
    }
    val models = linkedMapOf<UInt, IsRootDataModel>()
    val context = StoredModelDefinitionsContext()
    val metadataKeys = scan("meta", startKey = byteArrayOf(3), endKey = byteArrayOf(4), includeEnd = false).map { it.first }
    val storedModels = loadStoredModelDefinitions(metadataKeys) { metadataKey ->
        require(metadataKey.size == 5) { "Invalid stored model definition key" }
        var offset = 1
        val id = initUInt(reader = { metadataKey[offset++] })
        if (get("meta", migrationStateMetadataKey(id)) != null) {
            throw MigrationException("Finish the pending schema migration for model $id with its source-format version before upgrading storage")
        }
        val model = readStoredModelDefinition(id, context)
            ?: throw MigrationException("Missing source schema for model $id")
        models[id] = model
        model
    }
    for ((_, model) in storedModels) {
        if (model.Meta.keyDefinition.requiresKeyStorageFormatUpgrade()) {
            throw MigrationException("Model ${model.Meta.name} uses a primary key whose storage encoding changes; an explicit key/reference migration is required")
        }
    }
    // Original table values must be visited before one-way unique token identities.
    val namespaces = buildList {
        for ((id, model) in models) {
            if (model.hasFloatingPointStorage() || model.hasLegacyCollectionStorage()) {
                add("t:$id")
                add("k:$id")
                if (keepAllVersions) add("ht:$id")
            }
            if (model.hasLegacyTransportStorage()) {
                add("c:$id")
                if (keepUpdateHistoryIndex) add("uh:$id")
            }
        }
        for ((id, model) in models) {
            if (model.hasFloatingPointStorage()) {
                add("u:$id")
                if (keepAllVersions) addAll(listOf("hu:$id", "huk:$id"))
            }
            if (model.Meta.indexes.orEmpty().any { it.requiresStorageFormatUpgrade() }) {
                add("i:$id")
                if (keepAllVersions) addAll(listOf("hi:$id", "hik:$id"))
            }
        }
        add(ReceiptNamespace)
    }
    val adapter = object : StorageFormatUpgradeStore {
        override val namespaceCount = namespaces.size
        override val identity = namespaces.joinToString(",") + ",collection-count-proof-v1,allocation-proof-v1"
        private fun name(namespace: Int) = if (namespace == 0) "meta" else namespaces[namespace - 1]
        private fun store(namespace: Int) = name(namespace).let { if (it == ReceiptNamespace) "meta" else it }
        override suspend fun get(namespace: Int, key: ByteArray) = this@upgradeIndexedDbStorageFormat.get(store(namespace), key)
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt) =
            if (name(namespace) == ReceiptNamespace) {
                this@upgradeIndexedDbStorageFormat.scan("meta", startKey = after ?: ReceiptPrefix,
                    endKey = ReceiptPrefix.nextByteInSameLength(), includeStart = after == null, includeEnd = false, limit = limit)
                    .map { StorageFormatRow(it.first, it.second) }
            } else this@upgradeIndexedDbStorageFormat.scan(store(namespace), startKey = after, includeStart = false, limit = limit)
                .map { StorageFormatRow(it.first, it.second) }
        override suspend fun commit(writes: List<StorageFormatWrite>) {
            val operations = writes.map { write ->
                val value = write.value
                if (value == null) IndexedDbWriteOperation.Delete(store(write.namespace), write.key)
                else IndexedDbWriteOperation.Put(store(write.namespace), write.key, value)
            }.toMutableList()
            if (writes.any { it.namespace == 0 && it.key.contentEquals(StorageFormatMetadataKey) }) {
                operations += IndexedDbWriteOperation.Put("meta", OptionsMetadataKey, expected.encodeToByteArray())
            }
            writeBatch(operations)
        }
        override suspend fun preparePublication() {
            preparePublication.invoke()
            for ((id, model) in models) {
                if (decodeStorageFormat(this@upgradeIndexedDbStorageFormat.get("meta", sourceSchemaFormatMetadataKey(id))) != StorageFormat.CURRENT) {
                    // Publish each source schema and its codec marker together so reopening can decode either side of an interrupted rewrite.
                    this@upgradeIndexedDbStorageFormat.storeModelDefinition(id, model)
                }
            }
        }
        private val logEncryption = mutableMapOf<UInt, IndexedDbSensitiveFieldSupport>()
        private val values = models.mapValues { (id, model) -> FloatingPointStorageMigration(id, model, this, fieldEncryptionProvider) }
        private val allocations = models.mapValues { (id, model) ->
            LegacyIncrementingMapRecovery(id, model,
                read = { key -> this@upgradeIndexedDbStorageFormat.get("meta", key) },
                write = { key, value -> this@upgradeIndexedDbStorageFormat.put("meta", key, value) })
        }
        override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
            val name = name(namespace)
            if (name == ReceiptNamespace) return StorageFormatRow(row.key, tagReleasedTransactionReceipt(row.value))
            val id = name.substringAfter(':').toUInt()
            val model = models.getValue(id)
            val migration = values.getValue(id)
            if (name.startsWith("c:") || name.startsWith("uh:")) {
                val updateHistory = name.startsWith("uh:")
                if (row.value.isUnserializableChangeLogMarker() ||
                    updateHistory && (row.value.isHardDeleteTombstone() || !row.key.isUpdateHistoryRowKey(model.Meta.keyByteSize))) return row
                val record = if (updateHistory) row.key.copyOfRange(ULong.SIZE_BYTES, row.key.size)
                    else objectKeyBytesFromScopedRowKey(row.key)
                val version = if (updateHistory) row.key.readInvertedVersion() else row.key.readTrailingVersion()
                val sensitiveFields = logEncryption.getOrPut(id) {
                    IndexedDbSensitiveFieldSupport(mapOf(id to model), fieldEncryptionProvider)
                }
                val plain = sensitiveFields.decryptChangeLogPayloadIfNeeded(id, record, version, row.value)
                val converted = encodeVersionedChange(model, decodeVersionedChange(model, plain, legacyValueStorage = true))
                if (plain.contentEquals(converted)) return row
                return StorageFormatRow(row.key, sensitiveFields.encryptChangeLogPayload(id, record, version, converted))
            }
            if (name.startsWith("t:")) {
                // Released IndexedDB removed deleted table rows physically and rebuilt
                // complete snapshots, so retained zero counts prove present empties.
                val record = objectKeyBytesFromScopedRowKey(row.key)
                val start = createObjectRowKeyPrefix(record).size
                allocations.getValue(id).observe(record, row.key.copyOfRange(start, row.key.size))
                val converted = migration.convertCell(record, row.key.copyOfRange(start, row.key.size), row.value)
                return StorageFormatRow(createTableRowKey(record, converted.key), converted.value)
            }
            if (name.startsWith("k:") || name.startsWith("ht:")) {
                val current = name.startsWith("k:")
                val record = if (current) row.key else objectKeyBytesFromScopedRowKey(row.key)
                if (current) {
                    val meta = decodeRecordMeta(row.value)
                    allocations.getValue(id).verify(record, HLC.toStorageBytes(HLC(meta.firstVersion)), HLC.toStorageBytes(HLC(meta.lastVersion)),
                        verifyContinuousHistory = history@ {
                            if (!keepAllVersions) return@history false
                            val scope = createObjectRowKeyPrefix(record)
                            var after: ByteArray? = null
                            var creationPresent = false
                            var frontier = meta.firstVersion
                            while (true) {
                                val batch = this@upgradeIndexedDbStorageFormat.scan("ht:$id", startKey = after ?: scope,
                                    endKey = scope.nextByteInSameLength(), includeStart = after == null, includeEnd = false, limit = 64u)
                                if (batch.isEmpty()) break
                                for ((key, value) in batch) {
                                    if (key.size != scope.size + 8) return@history false
                                    var inverted = 0uL
                                    for (index in key.size - 8 until key.size) inverted = (inverted shl 8) or key[index].toUByte().toULong()
                                    val version = ULong.MAX_VALUE - inverted
                                    val (historicMeta, historicRows) = decodeHistoricSnapshot(value)
                                    if (historicMeta.firstVersion != meta.firstVersion || historicMeta.lastVersion != version ||
                                        version < meta.firstVersion || version > meta.lastVersion) return@history false
                                    creationPresent = creationPresent || version == meta.firstVersion
                                    frontier = maxOf(frontier, version)
                                    for ((qualifier, _) in historicRows) allocations.getValue(id).observe(record, qualifier)
                                }
                                after = batch.last().first
                            }
                            creationPresent && frontier == meta.lastVersion
                        })
                    if (row.value.size == 17) return row // metadata-only current rows have no snapshot
                }
                val (meta, rows) = decodeHistoricSnapshot(row.value)
                val converted = rows.map { (qualifier, value) ->
                    allocations.getValue(id).observe(record, qualifier)
                    migration.convertCell(record, qualifier, value).let { it.key to it.value }
                }
                return StorageFormatRow(row.key, encodeHistoricSnapshot(meta, converted))
            }
            if (name.startsWith("u:") || name.startsWith("hu:") || name.startsWith("huk:")) {
                val cleanup = name.startsWith("huk:")
                val start = if (cleanup) {
                    var position = 0
                    val length = initIntByVar { row.key[position++] }
                    position + length
                } else 0
                val end = row.key.size - if (name.startsWith("u:")) 0 else 8
                val converted = migration.convertUnique(row.key.copyOfRange(start, end))
                val target = row.key.copyOfRange(0, start) + converted + row.key.copyOfRange(end, row.key.size)
                return StorageFormatRow(target, if (cleanup) target.copyOfRange(start, target.size) else row.value)
            }
            val cleanup = name.startsWith("hik:")
            val historic = cleanup || name.startsWith("hi:")
            var offset = 0
            if (cleanup) {
                val recordKeySize = initIntByVar { row.key[offset++] }
                require(recordKeySize == model.Meta.keyByteSize) { "Invalid historic index cleanup identity" }
                offset += recordKeySize
            }
            val referenceStart = offset
            val referenceLength = initIntByVar { row.key[offset++] }
            require(referenceLength >= 0 && referenceLength <= row.key.size - offset) { "Invalid index reference length" }
            val reference = row.key.copyOfRange(offset, offset + referenceLength)
            offset += referenceLength
            val index = model.Meta.indexes.orEmpty().firstOrNull { it.toReleasedReferenceStorageByteArray().contentEquals(reference) }
                ?: return row // removed indexes are not interpreted or rewritten
            if (!index.requiresStorageFormatUpgrade()) return row
            val end = row.key.size - if (historic) 8 else 0
            val entry = row.key.copyOfRange(offset, end)
            val converted = if (index.requiresKeyStorageFormatUpgrade()) index.convertLegacyIndexBytes(entry, model.Meta.keyByteSize) else entry
            val descriptor = index.referenceStorageByteArray.bytes
            val descriptorPrefix = buildList {
                descriptor.size.writeVarBytes(::add)
                addAll(descriptor.toList())
            }.toByteArray()
            val newKey = row.key.copyOfRange(0, referenceStart) + descriptorPrefix + converted + row.key.copyOfRange(end, row.key.size)
            val value = if (cleanup) {
                var position = 0
                val length = initIntByVar { row.key[position++] }
                newKey.copyOfRange(position + length, newKey.size)
            } else row.value
            return StorageFormatRow(newKey, value)
        }
    }
    adapter.upgradeStorageFormat()
}
