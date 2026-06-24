package maryk.datastore.rocksdb.metadata

import maryk.file.File
import maryk.json.JsonToken
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
private const val CURRENT_VERSION = 1
internal const val LEGACY_INDEX_KEY_FORMAT_VERSION = 1
internal const val CURRENT_INDEX_KEY_FORMAT_VERSION = 2

fun readMetaFile(storePath: String): Map<UInt, ModelMeta> {
    return readStoreMetaFile(storePath).models
}

fun hasStoreMetaFile(storePath: String): Boolean =
    File.readText("$storePath/$META_FILE_NAME") != null

fun readStoreMetaFile(storePath: String): StoreMeta {
    val path = "$storePath/$META_FILE_NAME"
    val text = File.readText(path) ?: return StoreMeta(emptyMap(), LEGACY_INDEX_KEY_FORMAT_VERSION)
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

    File.writeText("$storePath/$META_FILE_NAME", builder.toString())
}

fun readModelNames(storePath: String): Map<UInt, String> =
    readMetaFile(storePath).mapValues { it.value.name }

fun readModelKeySizes(storePath: String): Map<UInt, Int> =
    readMetaFile(storePath).mapValues { it.value.keySize }

private fun parseMeta(text: String): StoreMeta {
    val models = mutableMapOf<UInt, ModelMeta>()
    var indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION

    val reader = YamlReader(text)

    var currentToken = reader.currentToken
    if (currentToken == JsonToken.StartDocument) {
        currentToken = reader.nextToken()
    }

    var token = currentToken
    if (token is JsonToken.StartObject) {
        token = reader.nextToken()
    }

    while (token !is JsonToken.EndObject && token !is JsonToken.EndDocument) {
        if (token is JsonToken.FieldName) {
            when (token.value) {
                "version" -> {
                    reader.nextToken() // consume version value
                }
                "indexKeyFormatVersion" -> {
                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                    indexKeyFormatVersion = (valueToken?.value as? Number)?.toIntExact()
                        ?: throw IllegalArgumentException("Store indexKeyFormatVersion should be an integer")
                }
                "models" -> {
                    var innerToken = reader.nextToken()
                    if (innerToken !is JsonToken.StartObject) {
                        token = innerToken
                        continue
                    }
                    innerToken = reader.nextToken()
                    while (innerToken !is JsonToken.EndObject) {
                        val idField = innerToken as? JsonToken.FieldName
                        val modelId = idField?.value?.toUIntOrNull()
                        var modelToken = reader.nextToken()
                        if (modelToken !is JsonToken.StartObject) {
                            reader.skipUntilNextField()
                            innerToken = reader.nextToken()
                            continue
                        }
                        var name: String? = null
                        var keySize: Int? = null

                        modelToken = reader.nextToken()
                        while (modelToken !is JsonToken.EndObject) {
                            val metaField = modelToken as? JsonToken.FieldName
                            when (metaField?.value) {
                                "name" -> {
                                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                                    name = valueToken?.value as? String
                                }
                                "keySize" -> {
                                    val valueToken = reader.nextToken() as? JsonToken.Value<*>
                                    keySize = (valueToken?.value as? Number)?.toIntExact()
                                }
                                else -> reader.skipUntilNextField()
                            }
                            modelToken = reader.nextToken()
                        }

                        if (modelId != null && name != null && keySize != null) {
                            models[modelId] = ModelMeta(name, keySize)
                        }

                        innerToken = reader.nextToken()
                    }
                }
                else -> reader.skipUntilNextField()
            }
        }
        token = reader.nextToken()
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
