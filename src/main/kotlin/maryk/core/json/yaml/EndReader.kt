package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Last char is already at '. Read until next ' */
internal class EndReader(
    yamlReader: YamlReader
) : YamlCharReader(yamlReader, null) {
    override fun readUntilToken() = JsonToken.EndJSON

    override fun handleReaderInterrupt() = JsonToken.EndJSON
}