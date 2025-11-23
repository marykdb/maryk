package maryk.datastore.rocksdb.metadata

import maryk.file.File
import maryk.json.JsonToken
import maryk.yaml.YamlReader
import maryk.yaml.YamlWriter

data class ModelMeta(
    val name: String,
    val keySize: Int,
)

private const val META_FILE_NAME = "MARYK_META.yml"
private const val CURRENT_VERSION = 1

fun readMetaFile(storePath: String): Map<UInt, ModelMeta> {
    val path = "$storePath/$META_FILE_NAME"
    val text = File.readText(path) ?: return emptyMap()
    return parseMeta(text)
}

fun writeMetaFile(storePath: String, models: Map<UInt, ModelMeta>) {
    val builder = StringBuilder()
    val writer = YamlWriter { builder.append(it) }

    writer.writeStartObject(isCompact = false)

    writer.writeFieldName("version")
    writer.writeInt(CURRENT_VERSION)

    writer.writeFieldName("models")
    writer.writeStartObject(isCompact = false)

    models.entries.sortedBy { it.key }.forEach { (id, meta) ->
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

private fun parseMeta(text: String): Map<UInt, ModelMeta> {
    val models = mutableMapOf<UInt, ModelMeta>()

    var idx = 0
    val reader = YamlReader(reader = {
        text.getOrNull(idx)?.also { idx++ }
            ?: throw IllegalStateException("Unexpected end of YAML while reading meta file")
    })

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
                                    keySize = (valueToken?.value as? Number)?.toInt()
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

    return models
}
