package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken

/** Reads comments and returns reading when done */
internal class CommentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    override fun readUntilToken(): JsonToken {
        while(!this.lastChar.isLineBreak()) {
            read()
        }

        this.parentReader.childIsDoneReading(false)
        @Suppress("UNCHECKED_CAST")
        return IndentReader(this.yamlReader, this.currentReader as P).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }
}
