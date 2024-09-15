@file:Suppress("FunctionName")

package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.json.IsJsonLikeReader
import maryk.json.TokenType
import maryk.yaml.YamlReader

const val MARYK_2018 = "tag:maryk.io,2018:"

private fun createReader(yaml: String): () -> Char {
    var index = 0
    return {
        yaml[index++].also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
}

private fun createYamlReader(reader: () -> Char, allowUnknownTags: Boolean): IsJsonLikeReader =
    YamlReader(
        defaultTag = MARYK_2018,
        tagMap = mapOf(MARYK_2018 to MARYK_TYPE_MAP),
        allowUnknownTags = allowUnknownTags,
        reader = reader
    )

fun MarykYamlModelReader(yaml: String): IsJsonLikeReader =
    createYamlReader(createReader(yaml), false)

fun MarykYamlModelReader(reader: () -> Char): IsJsonLikeReader =
    createYamlReader(reader, false)

fun MarykYamlReader(yaml: String): IsJsonLikeReader =
    createYamlReader(createReader(yaml), true)

fun MarykYamlReader(reader: () -> Char): IsJsonLikeReader =
    createYamlReader(reader, true)

val MARYK_TYPE_MAP: Map<String, TokenType> = buildMap {
    PropertyDefinitionType.entries.forEach {
        put(it.name, it)
    }
    IndexKeyPartType.cases().forEach {
        put(it.name, it)
    }
}
