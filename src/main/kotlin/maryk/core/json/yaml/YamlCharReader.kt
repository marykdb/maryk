package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal abstract class YamlCharReader(
    internal val yamlReader: YamlReader
) {
    val lastChar get() = this.yamlReader.lastChar

    var currentReader: YamlCharReader
        get() {
            return this.yamlReader.currentReader
        }
        set(reader) {
            this.yamlReader.currentReader = reader
        }

    fun read() = this.yamlReader.read()

    abstract fun readUntilToken(): JsonToken
    abstract fun handleReaderInterrupt(): JsonToken
}

internal interface IsYamlCharWithIndentsReader {
    fun indentCount(): Int
    fun indentCountForChildren(): Int
    fun continueIndentLevel(): JsonToken
    fun <P> newIndentLevel(parentReader: P): JsonToken where P : maryk.core.json.yaml.YamlCharReader,
                                                         P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
                                                         P : maryk.core.json.yaml.IsYamlCharWithIndentsReader
    fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken? = null): JsonToken
}

internal interface IsYamlCharWithChildrenReader {
    fun childIsDoneReading()
}

internal abstract class YamlCharWithParentReader<out P>(
    yamlReader: YamlReader,
    val parentReader: P
) : YamlCharReader(yamlReader)
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader