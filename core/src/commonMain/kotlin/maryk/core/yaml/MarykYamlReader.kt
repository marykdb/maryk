@file:Suppress("FunctionName")

package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.json.IsJsonLikeReader
import maryk.json.TokenType
import maryk.yaml.YamlReader

const val maryk2018 = "tag:maryk.io,2018:"

/** Creates a Yaml reader preset to read Maryk Models */
fun MarykYamlModelReader(
    yaml: String
): IsJsonLikeReader {
    var index = 0
    return MarykYamlModelReader {
        yaml[index++].also {
            if (it == '\u0000') {
                // So JS correctly exits at end of string
                throw Throwable("0 char encountered")
            }
        }
    }
}

/** Creates a Yaml reader preset to read Maryk Models */
@Suppress("FunctionName")
fun MarykYamlModelReader(
    reader: () -> Char
): IsJsonLikeReader =
    YamlReader(
        defaultTag = maryk2018,
        tagMap = mapOf(
            maryk2018 to marykTypeMap
        ),
        allowUnknownTags = false,
        reader = reader
    )

/** Creates a Yaml reader preset to read Maryk */
fun MarykYamlReader(
    yaml: String
): IsJsonLikeReader {
    var index = 0
    return MarykYamlReader {
        yaml[index++].also {
            if (it == '\u0000') {
                // So JS correctly exits at end of string
                throw Throwable("0 char encountered")
            }
        }
    }
}

/** Creates a Yaml reader preset to read Maryk */
@Suppress("FunctionName")
fun MarykYamlReader(
    reader: () -> Char
): IsJsonLikeReader =
    YamlReader(
        defaultTag = maryk2018,
        tagMap = mapOf(maryk2018 to marykTypeMap),
        allowUnknownTags = true,
        reader = reader
    )

val marykTypeMap: Map<String, TokenType> = arrayOf<Pair<String, TokenType>>()
    .plus(
        PropertyDefinitionType.values().map { it.name to it }
    )
    .plus(
        IndexKeyPartType.cases().map { it.name to it }
    ).toMap()
