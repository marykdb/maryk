package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Yaml reader with basic implementations for parents and indents */
internal abstract class YamlCharWithParentAndIndentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithIndentsReader {
    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        this.currentReader = this
        return this.readUntilToken(extraIndent, tag)
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ) =
        this.readUntilToken(0, tag)

    override fun indentCount() = this.parentReader.indentCount()

    @Suppress("UNCHECKED_CAST")
    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        this.parentReader.checkAndCreateFieldName(
            fieldName,
            isPlainStringReader
        )

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return parentReader.handleReaderInterrupt()
    }
}
