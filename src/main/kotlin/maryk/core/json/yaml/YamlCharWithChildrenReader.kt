package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal abstract class YamlCharWithChildrenReader(
    yamlReader: YamlReader,
    parentReader: YamlCharWithChildrenReader? = null
): YamlCharReader(yamlReader, parentReader) {
    abstract fun childIsDoneReading()
    abstract fun indentCount(): Int
    abstract fun continueIndentLevel(): JsonToken
    abstract fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken? = null): JsonToken
}