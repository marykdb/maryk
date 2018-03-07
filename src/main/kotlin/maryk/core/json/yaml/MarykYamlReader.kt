package maryk.core.json.yaml

import maryk.core.json.IsJsonLikeReader
import maryk.core.properties.definitions.PropertyDefinitionType

const val maryk2018 = "tag:maryk.io,2018:"

@Suppress("FunctionName")
fun MarykYamlReader(
    reader: () -> Char
) : IsJsonLikeReader =
    YamlReaderImpl(
        reader,
        maryk2018,
        mapOf(
            maryk2018 to marykTypeMap
        )
    )

val marykTypeMap: Map<String, PropertyDefinitionType> = PropertyDefinitionType.values().map {
    it.name to it
}.toMap()
