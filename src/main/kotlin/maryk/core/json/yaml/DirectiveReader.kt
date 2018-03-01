package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken

private val yamlRegEx = Regex("^YAML ([0-9]).([0-9]+)$")

/** Reads comments and returns reading when done */
internal class DirectiveReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    override fun readUntilToken(): JsonToken {
        var foundDirective = ""
        while(!this.lastChar.isLineBreak()) {
            foundDirective += lastChar
            read()
        }
        foundDirective = foundDirective.trimEnd()

        yamlRegEx.matchEntire(foundDirective)?.let {
            it.groups.let {
                if (this.yamlReader.version != null) {
                    throw InvalidYamlContent("Cannot declare yaml version twice")
                }
                if (it[1]?.value != "1") {
                    throw InvalidYamlContent("Unsupported Yaml major version")
                }
                this.yamlReader.version = "${it[1]?.value}.${it[2]?.value}"
            }
        }

        this.parentReader.childIsDoneReading()
        return this.parentReader.readUntilToken()
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }
}