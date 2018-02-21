package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal interface IsYamlCharReader {
    val lastChar: Char
    var currentReader: YamlCharReader
    fun read()
}

internal abstract class YamlCharReader(
    internal val yamlReader: YamlReaderImpl
) : IsYamlCharReader by yamlReader {
    abstract fun readUntilToken(): JsonToken
    abstract fun handleReaderInterrupt(): JsonToken
}

internal enum class IndentObjectType {
    UNKNOWN, OBJECT
}

internal interface IsYamlCharWithIndentsReader {
    fun indentCount(): Int
    fun indentCountForChildren(): Int
    fun continueIndentLevel(): JsonToken
    fun <P> newIndentLevel(parentReader: P): JsonToken where P : maryk.core.json.yaml.YamlCharReader,
                                                         P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
                                                         P : maryk.core.json.yaml.IsYamlCharWithIndentsReader
    fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken? = null): JsonToken
    fun foundIndentType(type: IndentObjectType): JsonToken?
}

internal interface IsYamlCharWithChildrenReader {
    fun childIsDoneReading()
}

internal abstract class YamlCharWithParentReader<out P>(
    yamlReader: YamlReaderImpl,
    val parentReader: P
) : YamlCharReader(yamlReader)
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader