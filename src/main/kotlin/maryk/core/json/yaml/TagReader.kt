package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Reads comments and returns reading when done */
internal class TagReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    var prefix = ""
    var tag = ""

    override fun readUntilToken(): JsonToken {
        read()

        while(!this.lastChar.isWhitespace()) {
            if(this.lastChar == '!') {
                // Double !!
                if(this.prefix.isEmpty()) {
                    this.prefix = "!" + this.tag + "!"
                    this.tag = ""
                } else {
                    throw InvalidYamlContent("Invalid tag"+this.tag)
                }
            } else {
                tag += this.lastChar
            }
            read()
        }
        // Single !
        if(this.prefix.isEmpty()) {
            this.prefix = "!"
        }

        this.parentReader.childIsDoneReading()
        return this.parentReader.continueIndentLevel(
            this.yamlReader.resolveTag(prefix, tag)
        )
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }
}