package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.json.IsJsonLikeReader
import maryk.yaml.YamlReader

const val maryk2018 = "tag:maryk.io,2018:"

/** Creates a Yaml reader preset to read Maryk Models */
@Suppress("FunctionName")
fun MarykYamlModelReader(
    reader: () -> Char
) : IsJsonLikeReader =
    YamlReader(
        defaultTag = maryk2018,
        tagMap = mapOf(
            maryk2018 to marykTypeMap
        ),
        allowUnknownTags = false,
        reader = reader
    )

/** Creates a Yaml reader preset to read Maryk */
@Suppress("FunctionName")
fun MarykYamlReader(
    reader: () -> Char
) : IsJsonLikeReader =
    YamlReader(
        defaultTag = maryk2018,
        tagMap = mapOf(maryk2018 to mapOf()),
        allowUnknownTags = true,
        reader = reader
    )

val marykTypeMap: Map<String, PropertyDefinitionType> = PropertyDefinitionType.values().map {
    it.name to it
}.toMap()
