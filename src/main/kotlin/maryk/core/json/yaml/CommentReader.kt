package maryk.core.json.yaml

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
        while(this.lastChar != '\n') {
            read()
        }

        this.parentReader.childIsDoneReading()
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