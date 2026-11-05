package maryk.datastore.rocksdb.metadata

import maryk.file.File
import maryk.file.writeTextViaTemporaryFile
import maryk.json.JsonToken
import maryk.yaml.InvalidYamlContent
import maryk.yaml.YamlReader
import maryk.yaml.YamlWriter

data class ModelMeta(
    val name: String,
    val keySize: Int,
) {
    init {
        require(keySize > 0) { "Model keySize should be positive but was $keySize" }
    }
}

data class StoreMeta(
    val models: Map<UInt, ModelMeta>,
    val indexKeyFormatVersion: Int = LEGACY_INDEX_KEY_FORMAT_VERSION,
) {
    init {
        require(indexKeyFormatVersion > 0) {
            "Store indexKeyFormatVersion should be positive but was $indexKeyFormatVersion"
        }
    }
}

private const val META_FILE_NAME = "MARYK_META.yml"
private const val CURRENT_VERSION = 2
private const val MAX_META_FILE_BYTES = 4 * 1024 * 1024
internal const val LEGACY_INDEX_KEY_FORMAT_VERSION = 1
internal const val CURRENT_INDEX_KEY_FORMAT_VERSION = 2

fun readMetaFile(storePath: String): Map<UInt, ModelMeta> {
    return readStoreMetaFile(storePath).models
}

fun hasStoreMetaFile(storePath: String): Boolean =
    File.size("$storePath/$META_FILE_NAME") != null

fun readStoreMetaFile(storePath: String): StoreMeta {
    val path = "$storePath/$META_FILE_NAME"
    if (File.size(path) == null) return StoreMeta(emptyMap(), LEGACY_INDEX_KEY_FORMAT_VERSION)
    val text = File.readText(path, MAX_META_FILE_BYTES)
        ?: throw IllegalStateException("Store metadata exceeds $MAX_META_FILE_BYTES bytes or changed while reading: $path")
    return parseMeta(text)
}

fun writeMetaFile(storePath: String, models: Map<UInt, ModelMeta>) {
    writeStoreMetaFile(storePath, StoreMeta(models, CURRENT_INDEX_KEY_FORMAT_VERSION))
}

fun writeStoreMetaFile(storePath: String, storeMeta: StoreMeta) {
    val builder = StringBuilder()
    val writer = YamlWriter { builder.append(it) }

    writer.writeStartObject(isCompact = false)

    writer.writeFieldName("version")
    writer.writeInt(CURRENT_VERSION)

    writer.writeFieldName("indexKeyFormatVersion")
    writer.writeInt(storeMeta.indexKeyFormatVersion)

    writer.writeFieldName("models")
    writer.writeStartObject(isCompact = false)

    storeMeta.models.entries.sortedBy { it.key }.forEach { (id, meta) ->
        writer.writeFieldName(id.toString())
        writer.writeStartObject(isCompact = false)

        writer.writeFieldName("name")
        writer.writeString(meta.name)

        writer.writeFieldName("keySize")
        writer.writeInt(meta.keySize)

        writer.writeEndObject()
    }

    writer.writeEndObject() // models
    writer.writeEndObject() // root

    File.writeTextViaTemporaryFile("$storePath/$META_FILE_NAME", builder.toString())
}

fun readModelNames(storePath: String): Map<UInt, String> =
    readMetaFile(storePath).mapValues { it.value.name }

fun readModelKeySizes(storePath: String): Map<UInt, Int> =
    readMetaFile(storePath).mapValues { it.value.keySize }

internal fun parseStoreMeta(text: String): StoreMeta = try {
    parseMeta(text, strict = true).also {
        require(it.indexKeyFormatVersion <= CURRENT_INDEX_KEY_FORMAT_VERSION) {
            "Unsupported backup index key format version ${it.indexKeyFormatVersion}"
        }
    }
} catch (error: InvalidYamlContent) {
    // YAML syntax errors derive from Error. Classify malformed backup data as
    // invalid input so inventory can isolate it without swallowing fatal errors.
    throw IllegalArgumentException("Malformed backup store metadata", error)
}

private fun parseMeta(text: String, strict: Boolean = false): StoreMeta {
    val models = mutableMapOf<UInt, ModelMeta>()
    var indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION
    var metaFileVersion: Int? = null

    val reader = YamlReader(text)

    var currentToken = reader.currentToken
    if (currentToken == JsonToken.StartDocument) {
        currentToken = reader.nextToken()
    }

    if (strict) require(currentToken is JsonToken.StartObject) { "Store metadata must be an object" }
    val rootFields = mutableSetOf<String>()
    var token = currentToken
    if (token is JsonToken.StartObject) {
        token = reader.nextToken()
    }

    while (token !is JsonToken.EndObject && token !is JsonToken.EndDocument) {
        if (token is JsonToken.FieldName) {
            if (strict) require(rootFields.add(token.value!!)) { "Duplicate store metadata field: ${token.value}" }
            when (token.value) {
                "version" -> {
                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                    metaFileVersion = (valueToken?.value as? Number)?.toIntExact()
                        ?: throw IllegalArgumentException("Store metadata version should be an integer")
                }
                "indexKeyFormatVersion" -> {
                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                    indexKeyFormatVersion = (valueToken?.value as? Number)?.toIntExact()
                        ?: throw IllegalArgumentException("Store indexKeyFormatVersion should be an integer")
                }
                "models" -> {
                    var innerToken = reader.nextToken()
                    if (strict) require(innerToken is JsonToken.StartObject) { "Store models metadata must be an object" }
                    if (innerToken !is JsonToken.StartObject) {
                        token = innerToken
                        continue
                    }
                    innerToken = reader.nextToken()
                    while (innerToken !is JsonToken.EndObject) {
                        require(innerToken !is JsonToken.EndDocument) { "Incomplete models metadata" }
                        val idField = innerToken as? JsonToken.FieldName
                        val modelId = idField?.value?.toUIntOrNull()
                        if (strict) require(modelId != null && modelId !in models) { "Invalid or duplicate model metadata id" }
                        var modelToken = reader.nextToken()
                        if (strict) require(modelToken is JsonToken.StartObject) { "Model metadata must be an object" }
                        if (modelToken !is JsonToken.StartObject) {
                            reader.skipUntilNextField()
                            innerToken = reader.nextToken()
                            continue
                        }
                        var name: String? = null
                        var keySize: Int? = null
                        val modelFields = mutableSetOf<String>()

                        modelToken = reader.nextToken()
                        while (modelToken !is JsonToken.EndObject) {
                            require(modelToken !is JsonToken.EndDocument) { "Incomplete model metadata" }
                            val metaField = modelToken as? JsonToken.FieldName
                            if (strict) require(metaField != null && modelFields.add(metaField.value!!)) { "Invalid or duplicate model metadata field" }
                            when (metaField?.value) {
                                "name" -> {
                                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                                    name = valueToken?.value as? String
                                }
                                "keySize" -> {
                                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                                    keySize = (valueToken?.value as? Number)?.toIntExact()
                                }
                                else -> {
                                    reader.skipUntilNextField()
                                    modelToken = reader.currentToken
                                    continue
                                }
                            }
                            modelToken = reader.nextToken()
                        }

                        if (strict) require(modelId != null && name != null && keySize != null) { "Incomplete model metadata" }
                        if (modelId != null && name != null && keySize != null) {
                            models[modelId] = ModelMeta(name, keySize)
                        }

                        innerToken = reader.nextToken()
                    }
                }
                else -> {
                    reader.skipUntilNextField()
                    token = reader.currentToken
                    continue
                }
            }
        }
        token = reader.nextToken()
    }

    if (strict) {
        require("models" in rootFields) { "Missing store model metadata" }
        require(token is JsonToken.EndObject && reader.nextToken() is JsonToken.EndDocument) {
            "Store metadata must contain exactly one complete object"
        }
        require(metaFileVersion == null || metaFileVersion > 0) { "Invalid store metadata version $metaFileVersion" }
    }
    require(metaFileVersion == null || metaFileVersion <= CURRENT_VERSION) {
        "Unsupported RocksDB metadata version $metaFileVersion; this Maryk version supports up to $CURRENT_VERSION"
    }

    return StoreMeta(models, indexKeyFormatVersion)
}

private fun Number.toIntExact(): Int =
    when (this) {
        is Byte -> toInt()
        is Short -> toInt()
        is Int -> this
        is Long -> {
            require(this in Int.MIN_VALUE..Int.MAX_VALUE) {
                "Model keySize should fit in Int but was $this"
            }
            toInt()
        }
        else -> throw IllegalArgumentException("Model keySize should be an integer but was $this")
    }
