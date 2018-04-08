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

    override fun indentCount() = this.parentReader.indentCount()

    @Suppress("UNCHECKED_CAST")
    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        this.parentReader.checkAndCreateFieldName(
            fieldName,
            isPlainStringReader
        )
}
