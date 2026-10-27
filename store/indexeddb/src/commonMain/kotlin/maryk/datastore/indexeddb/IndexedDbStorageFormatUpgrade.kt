package maryk.datastore.indexeddb

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUInt
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.hasFloatingPointValueObjects
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.requiresStorageFormatUpgrade
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.migration.FloatingPointStorageMigration
import maryk.datastore.shared.migration.StorageFormatRow
import maryk.datastore.indexeddb.processors.decodeVersionedChange
import maryk.datastore.indexeddb.processors.encodeVersionedChange
import maryk.datastore.indexeddb.processors.isUnserializableChangeLogMarker
import maryk.datastore.indexeddb.processors.readTrailingVersion
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeHistoricSnapshot
import maryk.datastore.shared.migration.StorageFormatUpgradeStore
import maryk.datastore.shared.migration.StorageFormatWrite
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.migration.upgradeStorageFormat

/** Invoked under the existing startup writer fence, before schema callbacks. */
internal suspend fun IndexedDbByteStore.upgradeIndexedDbStorageFormat(
    keepAllVersions: Boolean,
    keepUpdateHistoryIndex: Boolean,
    fieldEncryptionProvider: FieldEncryptionProvider?,
    preparePublication: suspend () -> Unit,
) {
    if (decodeStorageFormat(get("meta", StorageFormatMetadataKey)) == StorageFormat.CURRENT) return
    val options = get("meta", OptionsMetadataKey)?.decodeToString()
    val expected = indexedDbOptionsSummary(keepAllVersions, keepUpdateHistoryIndex)
    if (options != null && options != expected && options != expected.replace("schemaVersion=3", "schemaVersion=2")) {
        throw MigrationException("IndexedDB store options differ from the configured options")
    }
    val models = linkedMapOf<UInt, IsRootDataModel>()
    val context = DefinitionsConversionContext()
    for ((metadataKey, _) in scan("meta", startKey = byteArrayOf(3), endKey = byteArrayOf(4), includeEnd = false)) {
        require(metadataKey.size == 5) { "Invalid stored model definition key" }
        var offset = 1
        val id = initUInt(reader = { metadataKey[offset++] })
        if (get("meta", migrationStateMetadataKey(id)) != null) {
            throw MigrationException("Finish the pending schema migration for model $id with its source-format version before upgrading storage")
        }
        val model = readStoredModelDefinition(id, context)
            ?: throw MigrationException("Missing source schema for model $id")
        if (model.Meta.keyDefinition.requiresStorageFormatUpgrade()) {
            throw MigrationException("Model $id uses a floating-point primary key; an explicit key/reference migration is required")
        }
        models[id] = model
    }
    // Original table values must be visited before one-way unique token identities.
    val namespaces = buildList {
        for ((id, model) in models) {
            if (!model.hasFloatingPointStorage()) continue
            add("t:$id")
            if (keepAllVersions) add("ht:$id")
            if (model.hasFloatingPointValueObjects()) add("c:$id")
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
    }
    val adapter = object : StorageFormatUpgradeStore {
        override val namespaceCount = namespaces.size
        override val identity = namespaces.joinToString(",")
        private fun store(namespace: Int) = if (namespace == 0) "meta" else namespaces[namespace - 1]
        override suspend fun get(namespace: Int, key: ByteArray) = this@upgradeIndexedDbStorageFormat.get(store(namespace), key)
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt) =
            this@upgradeIndexedDbStorageFormat.scan(store(namespace), startKey = after, includeStart = false, limit = limit)
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
        override suspend fun preparePublication() = preparePublication.invoke()
        private val logEncryption = mutableMapOf<UInt, IndexedDbSensitiveFieldSupport>()
        private val values = models.mapValues { (id, model) -> FloatingPointStorageMigration(id, model, this, fieldEncryptionProvider) }
        override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
            val name = store(namespace)
            val id = name.substringAfter(':').toUInt()
            val model = models.getValue(id)
            val migration = values.getValue(id)
            if (name.startsWith("c:")) {
                if (row.value.isUnserializableChangeLogMarker()) return row
                val record = objectKeyBytesFromScopedRowKey(row.key)
                val version = row.key.readTrailingVersion()
                val sensitiveFields = logEncryption.getOrPut(id) {
                    IndexedDbSensitiveFieldSupport(mapOf(id to model), fieldEncryptionProvider)
                }
                val plain = sensitiveFields.decryptChangeLogPayloadIfNeeded(id, record, version, row.value)
                val converted = encodeVersionedChange(model, decodeVersionedChange(model, plain, legacyValueStorage = true))
                if (plain.contentEquals(converted)) return row
                return StorageFormatRow(row.key, sensitiveFields.encryptChangeLogPayload(id, record, version, converted))
            }
            if (name.startsWith("t:")) {
                val record = objectKeyBytesFromScopedRowKey(row.key)
                val start = createObjectRowKeyPrefix(record).size
                val converted = migration.convertCell(record, row.key.copyOfRange(start, row.key.size), row.value)
                return StorageFormatRow(createTableRowKey(record, converted.key), converted.value)
            }
            if (name.startsWith("ht:")) {
                val record = objectKeyBytesFromScopedRowKey(row.key)
                val (meta, rows) = decodeHistoricSnapshot(row.value)
                val converted = rows.map { (qualifier, value) -> migration.convertCell(record, qualifier, value).let { it.key to it.value } }
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
            val referenceLength = initIntByVar { row.key[offset++] }
            require(referenceLength >= 0 && referenceLength <= row.key.size - offset) { "Invalid index reference length" }
            val reference = row.key.copyOfRange(offset, offset + referenceLength)
            offset += referenceLength
            val index = model.Meta.indexes.orEmpty().firstOrNull { it.referenceStorageByteArray.bytes.contentEquals(reference) }
                ?: return row // removed indexes are not interpreted or rewritten
            if (!index.requiresStorageFormatUpgrade()) return row
            val end = row.key.size - if (historic) 8 else 0
            val converted = index.convertLegacyIndexBytes(row.key.copyOfRange(offset, end), model.Meta.keyByteSize)
            val newKey = row.key.copyOfRange(0, offset) + converted + row.key.copyOfRange(end, row.key.size)
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
