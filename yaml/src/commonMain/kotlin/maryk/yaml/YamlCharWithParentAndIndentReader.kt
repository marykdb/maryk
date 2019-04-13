package maryk.yaml

import maryk.json.JsonToken
import maryk.json.TokenType

/** Yaml reader with basic implementations for parents and indents */
internal abstract class YamlCharWithParentAndIndentReader<out P: IsYamlCharWithIndentsReader>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader {
    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        this.currentReader = this
        return this.readUntilToken(extraIndent, tag)
    }

    override fun indentCount() = this.parentReader.indentCount()

    @Suppress("UNCHECKED_CAST")
    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        this.parentReader.checkAndCreateFieldName(
            fieldName,
            isPlainStringReader
        )
}
