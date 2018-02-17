package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal enum class PlainStyleMode {
    NORMAL, FLOW_COLLECTION
}

/** Plain style string reader */
internal class PlainStringReader<out P>(
    yamlReader: YamlReader,
    parentReader: P,
    startWith: String = "",
//    val mode: PlainStyleMode = PlainStyleMode.NORMAL,
    private val jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader), IsYamlCharWithIndentsReader, IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var storedValue: String = startWith

    override fun readUntilToken(): JsonToken {
        loop@while(true) {
            if (lastChar == '\n') {
                this.storedValue = this.storedValue.trimEnd()
                return IndentReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            this.storedValue += lastChar
            read()
        }
    }

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.indentCount()

    override fun <P> newIndentLevel(parentReader: P)
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader = this.continueIndentLevel()

    override fun continueIndentLevel(): JsonToken {
        this.storedValue += ' '
        return this.readUntilToken()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        val token = this.handleReaderInterrupt()
        return if (indentCount == this.indentCount()) {
            token
        } else {
            this.parentReader.endIndentLevel(indentCount, token)
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading()
        this.currentReader.let {
            if (it is LineReader<*>) {
                this.currentReader = it.parentReader
            }
        }
        return this.jsonTokenConstructor(storedValue)
    }
}